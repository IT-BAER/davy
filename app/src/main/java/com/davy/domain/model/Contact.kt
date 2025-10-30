package com.davy.domain.model

import androidx.compose.runtime.Immutable

/**
 * Contact domain model.
 * 
 * Represents a vCard contact.
 */
@Immutable
data class Contact(
    val id: Long = 0,
    val addressBookId: Long,
    val uid: String,
    val contactUrl: String? = null,
    val etag: String? = null,
    
    // Name
    val displayName: String,
    val givenName: String? = null,
    val familyName: String? = null,
    val middleName: String? = null,
    val namePrefix: String? = null,
    val nameSuffix: String? = null,
    val nickname: String? = null,
    
    // Organization
    val organization: String? = null,
    val organizationUnit: String? = null,
    val jobTitle: String? = null,
    
    // Contact info
    val phoneNumbers: List<PhoneNumber> = emptyList(),
    val emails: List<Email> = emptyList(),
    val postalAddresses: List<PostalAddress> = emptyList(),
    val websites: List<String> = emptyList(),
    
    // Additional info
    val note: String? = null,
    val birthday: String? = null, // ISO 8601 date
    val anniversary: String? = null, // ISO 8601 date
    val photoBase64: String? = null,
    
    // Group support
    val categories: List<String> = emptyList(), // Group memberships (for CATEGORIES method)
    val isGroup: Boolean = false, // Whether this is a group vCard (KIND:group)
    val groupMembers: List<String> = emptyList(), // Member UIDs (for GROUP_VCARDS method)
    
    // Sync state
    val isDirty: Boolean = false,
    val isDeleted: Boolean = false,
    val androidContactId: Long? = null,
    val androidRawContactId: Long? = null,
    
    // Timestamps
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Gets full name in format: prefix given middle family suffix.
     */
    fun getFullName(): String {
        val parts = listOfNotNull(
            namePrefix,
            givenName,
            middleName,
            familyName,
            nameSuffix
        )
        return parts.joinToString(" ").ifBlank { displayName }
    }
    
    /**
     * Gets initials (first letters of given and family name).
     */
    fun getInitials(): String {
        val given = givenName?.firstOrNull()?.uppercase() ?: ""
        val family = familyName?.firstOrNull()?.uppercase() ?: ""
        return if (given.isNotEmpty() || family.isNotEmpty()) {
            "$given$family"
        } else {
            displayName.firstOrNull()?.uppercase() ?: "?"
        }
    }
    
    /**
     * Checks if contact has photo.
     */
    fun hasPhoto(): Boolean = photoBase64 != null
    
    /**
     * Checks if contact has phone numbers.
     */
    fun hasPhoneNumbers(): Boolean = phoneNumbers.isNotEmpty()
    
    /**
     * Checks if contact has emails.
     */
    fun hasEmails(): Boolean = emails.isNotEmpty()
    
    /**
     * Checks if contact has postal addresses.
     */
    fun hasPostalAddresses(): Boolean = postalAddresses.isNotEmpty()
    
    /**
     * Checks if contact is synced.
     */
    fun isSynced(): Boolean = contactUrl != null && etag != null
    
    /**
     * Gets primary phone number (first mobile or first of any type).
     */
    fun getPrimaryPhone(): PhoneNumber? {
        return phoneNumbers.firstOrNull { it.type == PhoneType.MOBILE }
            ?: phoneNumbers.firstOrNull()
    }
    
    /**
     * Gets primary email (first work or first of any type).
     */
    fun getPrimaryEmail(): Email? {
        return emails.firstOrNull { it.type == EmailType.WORK }
            ?: emails.firstOrNull()
    }
}

/**
 * Phone number with type.
 */
data class PhoneNumber(
    val number: String,
    val type: PhoneType = PhoneType.MOBILE,
    val label: String? = null // Custom label if type is CUSTOM
)

/**
 * Email address with type.
 */
data class Email(
    val email: String,
    val type: EmailType = EmailType.HOME,
    val label: String? = null // Custom label if type is CUSTOM
)

/**
 * Postal address with type.
 */
data class PostalAddress(
    val street: String? = null,
    val city: String? = null,
    val region: String? = null, // State/province
    val postalCode: String? = null,
    val country: String? = null,
    val type: AddressType = AddressType.HOME,
    val label: String? = null // Custom label if type is CUSTOM
) {
    /**
     * Formats address as multi-line string.
     */
    fun format(): String {
        val lines = mutableListOf<String>()
        
        if (!street.isNullOrBlank()) {
            lines.add(street)
        }
        
        val cityLine = listOfNotNull(
            city,
            region,
            postalCode
        ).joinToString(", ")
        
        if (cityLine.isNotBlank()) {
            lines.add(cityLine)
        }
        
        if (!country.isNullOrBlank()) {
            lines.add(country)
        }
        
        return lines.joinToString("\n")
    }
}

/**
 * Phone number type.
 */
enum class PhoneType {
    HOME,
    WORK,
    MOBILE,
    FAX_HOME,
    FAX_WORK,
    PAGER,
    OTHER,
    CUSTOM
}

/**
 * Email type.
 */
enum class EmailType {
    HOME,
    WORK,
    OTHER,
    CUSTOM
}

/**
 * Postal address type.
 */
enum class AddressType {
    HOME,
    WORK,
    OTHER,
    CUSTOM
}
