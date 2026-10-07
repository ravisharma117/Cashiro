package com.ritesh.cashiro.core

/**
 * Application-wide constants to avoid hardcoded values
 */
object Constants {
    
    /**
     * SMS Processing Configuration
     */
    object SmsProcessing {
        const val DEFAULT_BATCH_SIZE = 100
        const val SMS_PREVIEW_LENGTH = 200
        const val QUERY_LIMIT = 100
        const val INITIAL_SCAN_MONTHS = 3
        const val SCANNING_DELAY_MS = 3000L
    }
    
    /**
     * UI Configuration - Moved to ui/theme/Dimensions.kt for better organization
     * Keeping only non-dimension constants here
     */
    object UI {
        const val BUTTON_WIDTH_RATIO = 0.8f
        const val PROGRESS_STROKE_WIDTH = 2f
    }
    
    /**
     * Database Configuration
     */
    object Database {
        const val DATABASE_NAME = "cashiro_database"
        const val CURRENT_VERSION = 2
        const val TRANSACTION_HASH_DEFAULT = ""
    }
    
    /**
     * WorkManager Configuration
     */
    object WorkManager {
        const val SMS_READER_WORK_NAME = "sms_reader_work"
        const val PERIODIC_SCAN_INTERVAL_HOURS = 24L
        const val INITIAL_DELAY_MINUTES = 15L
    }
    
    /**
     * Parsing Configuration
     */
    object Parsing {
        const val MIN_MERCHANT_NAME_LENGTH = 2
        const val AMOUNT_SCALE = 2
        const val CONFIDENCE_PATTERN_BASED = 0.7f
        const val CONFIDENCE_AI_BASED = 0.9f
    }
    
    /**
     * Navigation Routes
     */
    object Routes {
        const val HOME = "home"
        const val TRANSACTIONS = "transactions"
        const val ANALYTICS = "analytics"
        const val CHAT = "chat"
        const val SETTINGS = "settings"
    }
    
    /**
     * LLM Model Configuration
     */
    object ModelDownload {
        const val MODEL_URL = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.task?download=true"
    
        const val MODEL_FILE_NAME = "qwen2.5-1.5b-instruct.task"
        const val MODEL_SIZE_MB = 1638L // ~1.6 GB
        const val MODEL_SIZE_BYTES = 1_500_000_000L // 1.5 GB minimum (handles decimal/binary variance)
        const val REQUIRED_SPACE_BYTES = 1_800_000_000L // 1.8 GB (1.1x model size for safety)
    }

    /**
     * External Links
     */
    object Links {
        /** The NAX IT Solutions API that mails PIN recovery codes. */
        const val API_BASE_URL = "https://naxits-api.netlify.app"
        const val GITHUB_URL = "https://github.com/ravisharma117/Cashiro"
        const val UPSTREAM_GITHUB_URL = "https://github.com/ritesh-kanwar/Cashiro"
        const val NEW_ISSUE_URL = "$GITHUB_URL/issues/new"
        const val WEBSITE_URL = "https://ritesh-kanwar.github.io/cashiro.showcase"
        const val PRIVACY_POLICY_URL = "$GITHUB_URL/blob/main/PRIVACY.md"
        const val TERMS_OF_SERVICE_URL = "https://ritesh-kanwar.github.io/cashiro.showcase/terms"
        const val FAQ_URL = "https://ritesh-kanwar.github.io/cashiro.showcase/faq"
        const val GUIDE_URL = "https://ritesh-kanwar.github.io/cashiro.showcase/guides"
        const val REPORT_BUG_URL = "$NEW_ISSUE_URL/choose"
    }
    object Device {
        val is64Bit = android.os.Build.SUPPORTED_ABIS.firstOrNull()
            ?.let { it == "arm64-v8a" || it == "x86_64" } == true
    }
}
