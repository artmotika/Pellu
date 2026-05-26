use anchor_lang::prelude::*;
use anchor_spl::token::{self, Mint, Token, TokenAccount, Transfer};

declare_id!("Fg6PaFpoGXkYsidMpWTK6W2BeZ7FEfcYkg476zPFsLnS");

#[program]
pub mod dfa_advanced_platform {
    use super::*;

    pub fn initialize_platform(
        ctx: Context<InitializePlatform>,
        asset_id: String,
        asset_name: String,
        total_supply: u64,
        legal_doc_hash: String,
        trade_unlock_timestamp: i64
    ) -> Result<()> {
        let registry = &mut ctx.accounts.asset_registry;
        registry.admin_pubkey = *ctx.accounts.admin.key;
        registry.compliance_pubkey = *ctx.accounts.admin.key;
        registry.mint = ctx.accounts.mint.key();
        registry.asset_id = asset_id;
        registry.asset_name = asset_name;
        registry.total_supply = total_supply;
        registry.legal_doc_hash = legal_doc_hash;
        registry.trade_unlock_timestamp = trade_unlock_timestamp;
        registry.is_active = true;
        registry.is_ipo_active = false;
        Ok(())
    }

    pub fn toggle_ipo(ctx: Context<AdminAction>, active: bool) -> Result<()> {
        let registry = &mut ctx.accounts.asset_registry;
        registry.is_ipo_active = active;
        Ok(())
    }

    pub fn initialize_voting(
        ctx: Context<InitializeVoting>,
        _action_id: String,
        title: String,
        options_count: u8,
        end_timestamp: i64
    ) -> Result<()> {
        let voting = &mut ctx.accounts.voting_account;
        voting.asset_registry = ctx.accounts.asset_registry.key();
        voting.title = title;
        voting.options_count = options_count;
        voting.votes_per_option = vec![0; options_count as usize];
        voting.end_timestamp = end_timestamp;
        voting.is_finalized = false;
        Ok(())
    }

    pub fn cast_vote(ctx: Context<CastVote>, option_index: u8) -> Result<()> {
        let voting = &mut ctx.accounts.voting_account;
        let clock = Clock::get()?;

        require!(clock.unix_timestamp < voting.end_timestamp, CustomError::VotingEnded);
        require!(!voting.is_finalized, CustomError::VotingFinalized);
        require!(option_index < voting.options_count, CustomError::InvalidOption);
        require!(ctx.accounts.user_account.is_kyc_approved, CustomError::KycNotApproved);
        require!(ctx.accounts.user_token_account.mint == ctx.accounts.asset_registry.mint, CustomError::WrongMint);
        require!(ctx.accounts.user_vote_record.has_voted == false, CustomError::AlreadyVoted);

        let weight = ctx.accounts.user_token_account.amount;
        require!(weight > 0, CustomError::NoTokens);

        voting.votes_per_option[option_index as usize] += weight;
        ctx.accounts.user_vote_record.has_voted = true;

        Ok(())
    }

    pub fn distribute_dividend(ctx: Context<DistributeDividend>, amount: u64) -> Result<()> {
        require!(ctx.accounts.admin.key() == ctx.accounts.asset_registry.admin_pubkey, CustomError::Unauthorized);
        
        let cpi_accounts = Transfer {
            from: ctx.accounts.source_token_account.to_account_info(),
            to: ctx.accounts.user_token_account.to_account_info(),
            authority: ctx.accounts.admin.to_account_info(),
        };
        token::transfer(CpiContext::new(ctx.accounts.token_program.to_account_info(), cpi_accounts), amount)?;
        Ok(())
    }

    pub fn register_user(ctx: Context<RegisterUser>) -> Result<()> {
        let user_account = &mut ctx.accounts.user_account;
        user_account.owner_pubkey = *ctx.accounts.user_wallet.key;
        user_account.is_kyc_approved = false;
        user_account.is_frozen = false;
        Ok(())
    }

    pub fn update_kyc_status(ctx: Context<ComplianceAction>, is_approved: bool) -> Result<()> {
        let user_account = &mut ctx.accounts.target_user_account;
        user_account.is_kyc_approved = is_approved;
        Ok(())
    }

    pub fn toggle_freeze_account(ctx: Context<ComplianceAction>, freeze: bool) -> Result<()> {
        let user_account = &mut ctx.accounts.target_user_account;
        user_account.is_frozen = freeze;
        Ok(())
    }

    pub fn trade_dfa(ctx: Context<TradeDfa>, amount: u64) -> Result<()> {
        let registry = &ctx.accounts.asset_registry;
        let clock = Clock::get()?;

        require!(ctx.accounts.seller_token_account.mint == registry.mint, CustomError::WrongMint);
        require!(ctx.accounts.buyer_token_account.mint == registry.mint, CustomError::WrongMint);
        require!(clock.unix_timestamp >= registry.trade_unlock_timestamp, CustomError::TradingLocked);
        require!(registry.is_active, CustomError::PlatformInactive);
        require!(ctx.accounts.seller_account.is_kyc_approved, CustomError::KycNotApproved);
        require!(ctx.accounts.buyer_account.is_kyc_approved, CustomError::KycNotApproved);
        require!(!ctx.accounts.seller_account.is_frozen, CustomError::AccountFrozen);
        require!(!ctx.accounts.buyer_account.is_frozen, CustomError::AccountFrozen);

        // Allow either seller OR admin to authorize the trade
        let is_admin = ctx.accounts.authority.key() == registry.admin_pubkey;
        let is_seller = ctx.accounts.authority.key() == ctx.accounts.seller_account.owner_pubkey;
        require!(is_admin || is_seller, CustomError::Unauthorized);

        let cpi_accounts = Transfer {
            from: ctx.accounts.seller_token_account.to_account_info(),
            to: ctx.accounts.buyer_token_account.to_account_info(),
            authority: ctx.accounts.authority.to_account_info(),
        };
        token::transfer(CpiContext::new(ctx.accounts.token_program.to_account_info(), cpi_accounts), amount)?;
        Ok(())
    }

    pub fn clawback_dfa(ctx: Context<ClawbackAction>, amount: u64) -> Result<()> {
        let cpi_accounts = Transfer {
            from: ctx.accounts.target_token_account.to_account_info(),
            to: ctx.accounts.destination_token_account.to_account_info(),
            authority: ctx.accounts.platform_authority.to_account_info(),
        };

        let bump = ctx.bumps.platform_authority;
        let seeds = &[
            b"platform_auth".as_ref(),
            &[bump],
        ];
        let signer = &[&seeds[..]];

        token::transfer(CpiContext::new_with_signer(ctx.accounts.token_program.to_account_info(), cpi_accounts, signer), amount)?;
        Ok(())
    }
}

#[cfg(test)]
mod tests;

#[derive(Accounts)]
#[instruction(asset_id: String)]
pub struct InitializePlatform<'info> {
    #[account(init, payer = admin, space = 8 + 32 + 32 + 32 + 64 + 64 + 8 + 128 + 8 + 1 + 1, seeds = [b"registry", asset_id.as_bytes()], bump)]
    pub asset_registry: Account<'info, AssetRegistry>,
    pub mint: Account<'info, Mint>,
    #[account(mut)] pub admin: Signer<'info>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct AdminAction<'info> {
    #[account(mut, has_one = admin_pubkey)]
    pub asset_registry: Account<'info, AssetRegistry>,
    pub admin_pubkey: Signer<'info>,
}

#[derive(Accounts)]
#[instruction(action_id: String)]
pub struct InitializeVoting<'info> {
    #[account(init, payer = admin, space = 8 + 32 + 128 + 1 + 2044 + 8 + 1, seeds = [b"voting", action_id.as_bytes()], bump)]
    pub voting_account: Account<'info, Voting>,
    pub asset_registry: Account<'info, AssetRegistry>,
    #[account(mut)] pub admin: Signer<'info>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct CastVote<'info> {
    #[account(mut)] pub voting_account: Account<'info, Voting>,
    pub asset_registry: Account<'info, AssetRegistry>,
    #[account(seeds = [b"user", user_wallet.key().as_ref()], bump)]
    pub user_account: Account<'info, UserAccount>,
    #[account(init, payer = user_wallet, space = 8 + 1, seeds = [b"vote_record", voting_account.key().as_ref(), user_wallet.key().as_ref()], bump)]
    pub user_vote_record: Account<'info, UserVote>,
    pub user_token_account: Account<'info, TokenAccount>,
    #[account(mut)] pub user_wallet: Signer<'info>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct DistributeDividend<'info> {
    pub asset_registry: Account<'info, AssetRegistry>,
    #[account(mut)] pub source_token_account: Account<'info, TokenAccount>,
    #[account(mut)] pub user_token_account: Account<'info, TokenAccount>,
    pub admin: Signer<'info>,
    pub token_program: Program<'info, Token>,
}

#[derive(Accounts)]
pub struct RegisterUser<'info> {
    #[account(init, payer = payer, space = 8 + 32 + 1 + 1, seeds = [b"user", user_wallet.key().as_ref()], bump)]
    pub user_account: Account<'info, UserAccount>,
    pub user_wallet: SystemAccount<'info>,
    #[account(mut)] pub payer: Signer<'info>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct ComplianceAction<'info> {
    #[account(has_one = compliance_pubkey)]
    pub asset_registry: Account<'info, AssetRegistry>,
    #[account(mut, seeds = [b"user", target_user_wallet.key().as_ref()], bump)]
    pub target_user_account: Account<'info, UserAccount>,
    pub target_user_wallet: SystemAccount<'info>,
    pub compliance_pubkey: Signer<'info>,
}

#[derive(Accounts)]
pub struct TradeDfa<'info> {
    pub asset_registry: Account<'info, AssetRegistry>,
    #[account(seeds = [b"user", seller_wallet.key().as_ref()], bump)]
    pub seller_account: Account<'info, UserAccount>,
    #[account(seeds = [b"user", buyer_wallet.key().as_ref()], bump)]
    pub buyer_account: Account<'info, UserAccount>,
    pub buyer_wallet: SystemAccount<'info>,
    pub seller_wallet: SystemAccount<'info>,
    #[account(mut)] pub seller_token_account: Account<'info, TokenAccount>,
    #[account(mut)] pub buyer_token_account: Account<'info, TokenAccount>,
    pub authority: Signer<'info>,
    pub token_program: Program<'info, Token>,
}

#[derive(Accounts)]
pub struct ClawbackAction<'info> {
    #[account(has_one = admin_pubkey)]
    pub asset_registry: Account<'info, AssetRegistry>,
    pub admin_pubkey: Signer<'info>,
    #[account(mut)] pub target_token_account: Account<'info, TokenAccount>,
    #[account(mut)] pub destination_token_account: Account<'info, TokenAccount>,
    #[account(seeds = [b"platform_auth"], bump)]
    pub platform_authority: AccountInfo<'info>,
    pub token_program: Program<'info, Token>,
}

#[account]
pub struct AssetRegistry {
    pub admin_pubkey: Pubkey,
    pub compliance_pubkey: Pubkey,
    pub mint: Pubkey,
    pub asset_id: String,
    pub asset_name: String,
    pub total_supply: u64,
    pub legal_doc_hash: String,
    pub trade_unlock_timestamp: i64,
    pub is_active: bool,
    pub is_ipo_active: bool
}

#[account]
pub struct Voting {
    pub asset_registry: Pubkey,
    pub title: String,
    pub options_count: u8,
    pub votes_per_option: Vec<u64>,
    pub end_timestamp: i64,
    pub is_finalized: bool
}

#[account]
pub struct UserAccount {
    pub owner_pubkey: Pubkey,
    pub is_kyc_approved: bool,
    pub is_frozen: bool
}

#[account]
pub struct UserVote {
    pub has_voted: bool,
}

#[error_code]
pub enum CustomError {
    #[msg("Wrong Mint")] WrongMint,
    #[msg("KYC not approved")] KycNotApproved,
    #[msg("Account frozen")] AccountFrozen,
    #[msg("Trading locked")] TradingLocked,
    #[msg("Platform inactive")] PlatformInactive,
    #[msg("Voting ended")] VotingEnded,
    #[msg("Voting finalized")] VotingFinalized,
    #[msg("Invalid option")] InvalidOption,
    #[msg("Already voted")] AlreadyVoted,
    #[msg("No tokens to vote")] NoTokens,
    #[msg("Unauthorized")] Unauthorized,
}
