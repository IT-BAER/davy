package com.davy.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Contact entity for Room database.
 * 
 * Represents a vCard contact from CardDAV.
 * 
 * @property id Local unique identifier (auto-generated)
 * @property addressBookId Foreign key to AddressBook entity
 * @property uid vCard UID (unique across server)
 * @property contactUrl CardDAV contact URL
 * @property etag ETag for conflict detection
 * @property displayName Full display name (FN in vCard)
 * @property givenName Given/first name
 * @property familyName Family/last name
 * @property middleName Middle name
 * @property namePrefix Prefix (Dr., Mr., etc.)
 * @property nameSuffix Suffix (Jr., III, etc.)
 * @property nickname Nickname
 * @property organization Organization name
 * @property organizationUnit Department/unit
 * @property jobTitle Job title
 * @property phoneNumbers JSON array of phone numbers with types
 * @property emails JSON array of email addresses with types
 * @property postalAddresses JSON array of postal addresses with types
 * @property websites JSON array of website URLs
 * @property note Notes/comments
 * @property birthday Birthday (ISO 8601 date)
 * @property anniversary Anniversary (ISO 8601 date)
 * @property photoBase64 Photo as base64 string
 * @property isDirty Whether contact has local changes
 * @property isDeleted Whether contact is marked for deletion
 * @property androidContactId Android ContactsContract contact ID
 * @property androidRawContactId Android ContactsContract raw contact ID
 * @property createdAt Creation timestamp (epoch millis)
 * @property updatedAt Last update timestamp (epoch millis)
 */
@Entity(
    tableName = "contacts",
    foreignKeys = [
        ForeignKey(
            entity = AddressBookEntity::class,
            parentColumns = ["id"],
            childColumns = ["address_book_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["address_book_id"]),
        Index(value = ["uid"]),
        Index(value = ["contact_url"], unique = true),
        Index(value = ["is_dirty"]),
        Index(value = ["is_deleted"]),
        Index(value = ["android_contact_id"]),
        Index(value = ["android_raw_contact_id"])
    ]
)
data class ContactEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @androidx.room.ColumnInfo(name = "address_book_id")
    val addressBookId: Long,
    
    @androidx.room.ColumnInfo(name = "uid")
    val uid: String,
    
    @androidx.room.ColumnInfo(name = "contact_url")
    val contactUrl: String? = null,
    
    @androidx.room.ColumnInfo(name = "etag")
    val etag: String? = null,
    
    // Name fields
    @androidx.room.ColumnInfo(name = "display_name")
    val displayName: String,
    
    @androidx.room.ColumnInfo(name = "given_name")
    val givenName: String? = null,
    
    @androidx.room.ColumnInfo(name = "family_name")
    val familyName: String? = null,
    
    @androidx.room.ColumnInfo(name = "middle_name")
    val middleName: String? = null,
    
    @androidx.room.ColumnInfo(name = "name_prefix")
    val namePrefix: String? = null,
    
    @androidx.room.ColumnInfo(name = "name_suffix")
    val nameSuffix: String? = null,
    
    @androidx.room.ColumnInfo(name = "nickname")
    val nickname: String? = null,
    
    // Organization fields
    @androidx.room.ColumnInfo(name = "organization")
    val organization: String? = null,
    
    @androidx.room.ColumnInfo(name = "organization_unit")
    val organizationUnit: String? = null,
    
    @androidx.room.ColumnInfo(name = "job_title")
    val jobTitle: String? = null,
    
    // Contact info (stored as JSON)
    @androidx.room.ColumnInfo(name = "phone_numbers")
    val phoneNumbers: String? = null, // JSON: [{"number": "123", "type": "MOBILE"}]
    
    @androidx.room.ColumnInfo(name = "emails")
    val emails: String? = null, // JSON: [{"email": "test@example.com", "type": "HOME"}]
    
    @androidx.room.ColumnInfo(name = "postal_addresses")
    val postalAddresses: String? = null, // JSON: [{"street": "...", "city": "...", "type": "HOME"}]
    
    @androidx.room.ColumnInfo(name = "websites")
    val websites: String? = null, // JSON: ["https://example.com"]
    
    // Additional fields
    @androidx.room.ColumnInfo(name = "note")
    val note: String? = null,
    
    @androidx.room.ColumnInfo(name = "birthday")
    val birthday: String? = null, // ISO 8601 date
    
    @androidx.room.ColumnInfo(name = "anniversary")
    val anniversary: String? = null, // ISO 8601 date
    
    @androidx.room.ColumnInfo(name = "photo_base64")
    val photoBase64: String? = null,
    
    // Sync state
    @androidx.room.ColumnInfo(name = "is_dirty")
    val isDirty: Boolean = false,
    
    @androidx.room.ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false,
    
    // Android integration
    @androidx.room.ColumnInfo(name = "android_contact_id")
    val androidContactId: Long? = null,
    
    @androidx.room.ColumnInfo(name = "android_raw_contact_id")
    val androidRawContactId: Long? = null,
    
    // Timestamps
    @androidx.room.ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    
    @androidx.room.ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
