use anchor_lang::prelude::*;
use anchor_spl::token::{self, Mint, Token, TokenAccount, Transfer};

declare_id!("DfaPlatform22222222222222222222222222222222");

#[program]
pub mod dfa_advanced_platform {
    use super::*;

    /// Инициализация платформы и реестра актива с привязкой к юридическому документу
    pub fn initialize_platform(
        ctx: Context<InitializePlatform>,
        asset_name: String,
        total_supply: u64,
        legal_doc_hash: String, // Хэш PDF-файла "Решение о выпуске ЦФА" (например, IPFS CID)
        trade_unlock_timestamp: i64 // Unix-время, до которого вторичные торги запрещены
    ) -> Result<()> {
        let registry = &mut ctx.accounts.asset_registry;
        registry.admin_pubkey = *ctx.accounts.admin.key;
        registry.compliance_pubkey = *ctx.accounts.admin.key; // По умолчанию админ и комплаенс — одно лицо
        registry.asset_name = asset_name;
        registry.total_supply = total_supply;
        registry.legal_doc_hash = legal_doc_hash;
        registry.trade_unlock_timestamp = trade_unlock_timestamp;
        registry.is_active = true;
        Ok(())
    }

    /// Назначение отдельного офицера по комплаенсу (Разделение ролей - RBAC)
    pub fn set_compliance_officer(ctx: Context<AdminAction>, new_compliance: Pubkey) -> Result<()> {
        let registry = &mut ctx.accounts.asset_registry;
        registry.compliance_pubkey = new_compliance;
        Ok(())
    }

    /// Регистрация кошелька в системе (Создание PDA)
    pub fn register_user(ctx: Context<RegisterUser>) -> Result<()> {
        let user_account = &mut ctx.accounts.user_account;
        user_account.owner_pubkey = *ctx.accounts.user_wallet.key;
        user_account.is_kyc_approved = false; // По умолчанию KYC не пройден
        user_account.is_frozen = false;       // По умолчанию счет не заморожен
        Ok(())
    }

    /// Обновление KYC статуса (Только для роли Compliance)
    pub fn update_kyc_status(ctx: Context<ComplianceAction>, is_approved: bool) -> Result<()> {
        let user_account = &mut ctx.accounts.target_user_account;
        user_account.is_kyc_approved = is_approved;
        Ok(())
    }

    /// Заморозка счета (AML контроль)
    pub fn toggle_freeze_account(ctx: Context<ComplianceAction>, freeze: bool) -> Result<()> {
        let user_account = &mut ctx.accounts.target_user_account;
        user_account.is_frozen = freeze;
        Ok(())
    }

    /// Вторичные торги между проверенными участниками
    pub fn trade_dfa(ctx: Context<TradeDfa>, amount: u64) -> Result<()> {
        let registry = &ctx.accounts.asset_registry;
        let clock = Clock::get()?;

        // 1. Проверка временной блокировки (Lock-up period)
        require!(clock.unix_timestamp >= registry.trade_unlock_timestamp, CustomError::TradingLocked);
        require!(registry.is_active, CustomError::PlatformInactive);

        // 2. Строгое ЦФА-правило: KYC обоих участников
        require!(ctx.accounts.seller_account.is_kyc_approved, CustomError::KycNotApproved);
        require!(ctx.accounts.buyer_account.is_kyc_approved, CustomError::KycNotApproved);

        // 3. Проверка на заморозку (AML)
        require!(!ctx.accounts.seller_account.is_frozen, CustomError::AccountFrozen);
        require!(!ctx.accounts.buyer_account.is_frozen, CustomError::AccountFrozen);

        // 4. Перевод токенов (CPI вызов в Token Program)
        let cpi_accounts = Transfer {
            from: ctx.accounts.seller_token_account.to_account_info(),
            to: ctx.accounts.buyer_token_account.to_account_info(),
            authority: ctx.accounts.seller.to_account_info(),
        };
        let cpi_program = ctx.accounts.token_program.to_account_info();
        let cpi_ctx = CpiContext::new(cpi_program, cpi_accounts);
        token::transfer(cpi_ctx, amount)?;

        Ok(())
    }

    /// Принудительное изъятие активов по решению суда (Clawback).
    /// Выполняется через PDA-авторизацию, минуя подпись пользователя.
    pub fn clawback_dfa(ctx: Context<ClawbackAction>, amount: u64, _reason_code: String) -> Result<()> {
        // Проверка: операцию вызывает только Админ (реализуется через макросы Accounts)

        let cpi_accounts = Transfer {
            from: ctx.accounts.target_token_account.to_account_info(),
            to: ctx.accounts.destination_token_account.to_account_info(),
            authority: ctx.accounts.target_token_authority.to_account_info(), // PDA платформы имеет право двигать токены
        };

        // В реальном проде здесь генерируются seeds для PDA, который является делегатом (Delegate)
        // или владельцем SPL Token аккаунта пользователя.
        let platform_seeds: &[&[&[u8]]] = &[&[b"platform_auth", &[ctx.bumps.target_token_authority]]];
        let cpi_program = ctx.accounts.token_program.to_account_info();
        let cpi_ctx = CpiContext::new_with_signer(cpi_program, cpi_accounts, platform_seeds);

        token::transfer(cpi_ctx, amount)?;

        Ok(())
    }
}

// --- СТРУКТУРЫ ВАЛИДАЦИИ АККАУНТОВ ---

#[derive(Accounts)]
pub struct InitializePlatform<'info> {
    #[account(init, payer = admin, space = 8 + 32 + 32 + 64 + 8 + 64 + 8 + 1)]
    pub asset_registry: Account<'info, AssetRegistry>,
    #[account(mut)]
    pub admin: Signer<'info>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct AdminAction<'info> {
    #[account(mut, has_one = admin)]
    pub asset_registry: Account<'info, AssetRegistry>,
    pub admin: Signer<'info>,
}

#[derive(Accounts)]
pub struct RegisterUser<'info> {
    #[account(init, payer = payer, space = 8 + 32 + 1 + 1, seeds = [b"user", user_wallet.key().as_ref()], bump)]
    pub user_account: Account<'info, UserAccount>,
    pub user_wallet: SystemAccount<'info>,
    #[account(mut)]
    pub payer: Signer<'info>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct ComplianceAction<'info> {
    #[account(has_one = compliance_pubkey)]
    pub asset_registry: Account<'info, AssetRegistry>,
    #[account(mut)]
    pub target_user_account: Account<'info, UserAccount>,
    pub compliance_pubkey: Signer<'info>,
}

#[derive(Accounts)]
pub struct TradeDfa<'info> {
    pub asset_registry: Account<'info, AssetRegistry>,
    pub seller_account: Account<'info, UserAccount>,
    pub buyer_account: Account<'info, UserAccount>,
    #[account(mut)] pub seller_token_account: Account<'info, TokenAccount>,
    #[account(mut)] pub buyer_token_account: Account<'info, TokenAccount>,
    pub seller: Signer<'info>,
    pub token_program: Program<'info, Token>,
}

#[derive(Accounts)]
pub struct ClawbackAction<'info> {
    #[account(has_one = admin)]
    pub asset_registry: Account<'info, AssetRegistry>,
    pub admin: Signer<'info>,
    #[account(mut)] pub target_token_account: Account<'info, TokenAccount>,
    #[account(mut)] pub destination_token_account: Account<'info, TokenAccount>,
    /// CHECK: Это PDA программы, который назначен Authority над аккаунтами для обеспечения Clawback
    #[account(seeds = [b"platform_auth"], bump)]
    pub target_token_authority: AccountInfo<'info>,
    pub token_program: Program<'info, Token>,
}

// --- ХРАНИЛИЩА СОСТОЯНИЯ (STATE) ---

#[account]
pub struct AssetRegistry {
    pub admin_pubkey: Pubkey,            // Владелец контракта
    pub compliance_pubkey: Pubkey,       // Офицер, имеющий право одобрять KYC и морозить счета
    pub asset_name: String,
    pub total_supply: u64,
    pub legal_doc_hash: String,          // Хэш решения о выпуске (IPFS)
    pub trade_unlock_timestamp: i64,     // Timestamp разлока торгов
    pub is_active: bool
}

#[account]
pub struct UserAccount {
    pub owner_pubkey: Pubkey,
    pub is_kyc_approved: bool,           // Статус идентификации
    pub is_frozen: bool                  // AML статус (true = заблокирован)
}

// --- КАСТОМНЫЕ ОШИБКИ ---

#[error_code]
pub enum CustomError {
    #[msg("Both parties must have an approved KYC status.")]
    KycNotApproved,
    #[msg("One of the accounts is frozen due to AML policies.")]
    AccountFrozen,
    #[msg("Trading is locked until the maturity date is reached.")]
    TradingLocked,
    #[msg("The platform is currently inactive or paused.")]
    PlatformInactive,
}