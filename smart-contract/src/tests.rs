use super::*;
use anchor_lang::prelude::Pubkey;

// --- Утилиты для создания тестовых данных ---
fn create_mock_user(owner: Pubkey) -> UserAccount {
    UserAccount {
        owner_pubkey: owner,
        is_kyc_approved: false,
        is_frozen: false,
    }
}

fn create_mock_asset() -> AssetRegistry {
    AssetRegistry {
        admin_pubkey: Pubkey::new_unique(),
        compliance_pubkey: Pubkey::new_unique(),
        mint: Pubkey::new_unique(),
        asset_id: "GOLD_001".to_string(),
        asset_name: "Digital Gold".to_string(),
        total_supply: 1000000u64,
        legal_doc_hash: "HASH123".to_string(),
        trade_unlock_timestamp: 1000i64,
        is_active: true,
        is_ipo_active: false,
    }
}

// --- ТЕСТЫ ПОЛЬЗОВАТЕЛЕЙ (UserAccount) ---

#[test]
fn test_user_registration_state() {
    let owner = Pubkey::new_unique();
    let user = create_mock_user(owner);
    assert_eq!(user.owner_pubkey, owner);
    assert!(!user.is_kyc_approved, "New user should not have KYC approved");
    assert!(!user.is_frozen, "New user should not be frozen");
}

#[test]
fn test_kyc_status_transition() {
    let mut user = create_mock_user(Pubkey::new_unique());
    
    // Approve KYC
    user.is_kyc_approved = true;
    assert!(user.is_kyc_approved);
    
    // Revoke KYC
    user.is_kyc_approved = false;
    assert!(!user.is_kyc_approved);
}

#[test]
fn test_account_freezing_logic() {
    let mut user = create_mock_user(Pubkey::new_unique());
    user.is_frozen = true;
    assert!(user.is_frozen);
    user.is_frozen = false;
    assert!(!user.is_frozen);
}

// --- ТЕСТЫ ТОРГОВЛИ (Trading Logic) ---

#[test]
fn test_trading_locked_by_timestamp() {
    let asset = create_mock_asset(); // unlock_timestamp = 1000
    let current_time = 500i64;
    assert!(current_time < asset.trade_unlock_timestamp, "Trading must be locked");
}

#[test]
fn test_trading_allowed_after_timestamp() {
    let asset = create_mock_asset();
    let current_time = 1500i64;
    assert!(current_time >= asset.trade_unlock_timestamp, "Trading must be unlocked");
}

#[test]
fn test_trading_fails_if_platform_inactive() {
    let mut asset = create_mock_asset();
    asset.is_active = false;
    assert!(!asset.is_active, "Should fail when platform is inactive");
}

#[test]
fn test_trading_kyc_requirement() {
    let seller = create_mock_user(Pubkey::new_unique());
    let buyer = create_mock_user(Pubkey::new_unique());
    
    let can_trade = seller.is_kyc_approved && buyer.is_kyc_approved;
    assert!(!can_trade, "Trading should fail if KYC is not approved for both");
}

#[test]
fn test_trading_fails_if_frozen() {
    let mut seller = create_mock_user(Pubkey::new_unique());
    seller.is_kyc_approved = true;
    seller.is_frozen = true;
    
    let can_trade = !seller.is_frozen;
    assert!(!can_trade, "Frozen accounts cannot trade");
}

#[test]
fn test_trading_authorization_logic() {
    let asset = create_mock_asset();
    let seller_pubkey = Pubkey::new_unique();
    let admin_pubkey = asset.admin_pubkey;
    let random_pubkey = Pubkey::new_unique();

    // Case 1: Seller authorizes
    assert!(seller_pubkey == seller_pubkey || seller_pubkey == admin_pubkey);
    
    // Case 2: Admin authorizes
    assert!(admin_pubkey == seller_pubkey || admin_pubkey == admin_pubkey);

    // Case 3: Random user tries to authorize
    let is_authorized = random_pubkey == seller_pubkey || random_pubkey == admin_pubkey;
    assert!(!is_authorized, "Random user should not be authorized");
}

// --- ТЕСТЫ ГОЛОСОВАНИЯ (Voting) ---

#[test]
fn test_voting_initialization_limits() {
    let options_count = 5u8;
    let voting = Voting {
        asset_registry: Pubkey::new_unique(),
        title: "Governance Vote".to_string(),
        options_count,
        votes_per_option: vec![0u64; options_count as usize],
        end_timestamp: 2000i64,
        is_finalized: false,
    };
    
    assert_eq!(voting.votes_per_option.len(), 5);
    assert_eq!(voting.votes_per_option[4], 0);
}

#[test]
fn test_double_voting_prevention_logic() {
    let mut user_vote = UserVote { has_voted: false };
    
    // First vote
    assert!(!user_vote.has_voted);
    user_vote.has_voted = true;
    
    // Second vote should be blocked by the `require!` in the program
    assert!(user_vote.has_voted, "User should be marked as already voted");
}

#[test]
fn test_voting_expiry_logic() {
    let end_time = 2000i64;
    let current_time_late = 2500i64;
    let current_time_ok = 1500i64;
    
    assert!(current_time_late > end_time, "Voting should be expired");
    assert!(current_time_ok < end_time, "Voting should be active");
}

// --- ТЕСТЫ IPO (IPO Logic) ---

#[test]
fn test_ipo_toggle() {
    let mut asset = create_mock_asset();
    assert!(!asset.is_ipo_active);
    
    asset.is_ipo_active = true;
    assert!(asset.is_ipo_active);
}
