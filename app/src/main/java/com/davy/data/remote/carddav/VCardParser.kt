package com.davy.data.remote.carddav

import com.davy.domain.model.AddressType
import com.davy.domain.model.Contact
import com.davy.domain.model.Email
import com.davy.domain.model.EmailType
import com.davy.domain.model.PhoneNumber
import com.davy.domain.model.PhoneType
import com.davy.domain.model.PostalAddress
import ezvcard.Ezvcard
import ezvcard.VCard
import ezvcard.parameter.AddressType as VCardAddressType
import ezvcard.parameter.EmailType as VCardEmailType
import ezvcard.parameter.TelephoneType as VCardTelephoneType
import ezvcard.property.Address
import ezvcard.property.Birthday
import ezvcard.property.Photo
import ezvcard.property.StructuredName
import ezvcard.property.Telephone
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * vCard parser using ez-vcard library.
 * Parses vCard 3.0 and 4.0 formats to Contact domain model.
 *
 * Supported vCard properties:
 * - FN: Formatted name
 * - N: Structured name (family, given, middle, prefix, suffix)
 * - NICKNAME: Nickname
 * - ORG: Organization and organizational unit
 * - TITLE: Job title
 * - TEL: Phone numbers with types
 * - EMAIL: Email addresses with types
 * - ADR: Postal addresses with types
 * - URL: Websites
 * - NOTE: Notes
 * - BDAY: Birthday
 * - ANNIVERSARY: Anniversary
 * - PHOTO: Photo (base64 encoded)
 */
@Singleton
class VCardParser @Inject constructor() {

    /**
     * Parses a vCard string to a Contact domain model.
     *
     * @param vcardData The vCard data as a string
     * @param addressBookId The ID of the address book this contact belongs to
     * @param contactUrl The URL of this contact resource
     * @param etag The ETag of this contact (for sync tracking)
     * @return Contact domain model, or null if parsing fails
     */
    fun parse(
        vcardData: String,
        addressBookId: Long,
        contactUrl: String,
        etag: String?
    ): Contact? {
        return try {
            // Parse vCard using ez-vcard
            val vcard = Ezvcard.parse(vcardData).first() ?: return null

            // Extract UID (required)
            val uid = vcard.uid?.value ?: return null

            // Extract name fields
            val structuredName = vcard.structuredName
            val displayName = vcard.formattedName?.value ?: buildDisplayName(structuredName)
            val givenName = structuredName?.given
            val familyName = structuredName?.family
            val middleName = structuredName?.additionalNames?.firstOrNull()
            val namePrefix = structuredName?.prefixes?.firstOrNull()
            val nameSuffix = structuredName?.suffixes?.firstOrNull()
            val nickname = vcard.nickname?.values?.firstOrNull()

            // Extract organization
            val org = vcard.organization
            val organization = org?.values?.firstOrNull()
            val organizationUnit = org?.values?.getOrNull(1)
            val jobTitle = vcard.titles?.firstOrNull()?.value

            // Extract phone numbers
            val phoneNumbers = vcard.telephoneNumbers.map { tel ->
                parsePhoneNumber(tel)
            }

            // Extract emails
            val emails = vcard.emails.map { email ->
                parseEmail(email)
            }

            // Extract postal addresses
            val postalAddresses = vcard.addresses.map { addr ->
                parsePostalAddress(addr)
            }

            // Extract websites
            val websites = vcard.urls.map { it.value }

            // Extract note
            val note = vcard.notes?.firstOrNull()?.value

            // Extract birthday
            val birthday = parseBirthday(vcard.birthday)

            // Extract anniversary
            val anniversary = parseAnniversary(vcard.anniversary)

            // Extract photo
            val photoBase64 = parsePhoto(vcard.photos?.firstOrNull())

            // Build Contact
            Contact(
                id = 0, // Will be set by Room
                addressBookId = addressBookId,
                uid = uid,
                contactUrl = contactUrl,
                etag = etag,
                displayName = displayName,
                givenName = givenName,
                familyName = familyName,
                middleName = middleName,
                namePrefix = namePrefix,
                nameSuffix = nameSuffix,
                nickname = nickname,
                organization = organization,
                organizationUnit = organizationUnit,
                jobTitle = jobTitle,
                phoneNumbers = phoneNumbers,
                emails = emails,
                postalAddresses = postalAddresses,
                websites = websites,
                note = note,
                birthday = birthday,
                anniversary = anniversary,
                photoBase64 = photoBase64,
                isDirty = false,
                isDeleted = false,
                androidContactId = null,
                androidRawContactId = null,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            // Log error and return null
            e.printStackTrace()
            null
        }
    }

    /**
     * Builds a display name from structured name components.
     */
    private fun buildDisplayName(structuredName: StructuredName?): String {
        if (structuredName == null) return ""

        val parts = listOfNotNull(
            structuredName.prefixes?.firstOrNull(),
            structuredName.given,
            structuredName.additionalNames?.firstOrNull(),
            structuredName.family,
            structuredName.suffixes?.firstOrNull()
        )

        return parts.joinToString(" ").trim()
    }

    /**
     * Parses a vCard Telephone to PhoneNumber domain model.
     */
    private fun parsePhoneNumber(tel: Telephone): PhoneNumber {
        val number = tel.text ?: tel.uri?.number ?: ""
        
        // Map vCard telephone types to domain PhoneType
        val type = when {
            tel.types.contains(VCardTelephoneType.CELL) -> PhoneType.MOBILE
            tel.types.contains(VCardTelephoneType.HOME) -> PhoneType.HOME
            tel.types.contains(VCardTelephoneType.WORK) -> PhoneType.WORK
            tel.types.contains(VCardTelephoneType.FAX) && tel.types.contains(VCardTelephoneType.HOME) -> PhoneType.FAX_HOME
            tel.types.contains(VCardTelephoneType.FAX) && tel.types.contains(VCardTelephoneType.WORK) -> PhoneType.FAX_WORK
            tel.types.contains(VCardTelephoneType.PAGER) -> PhoneType.PAGER
            else -> {
                // Check for custom type
                val customType = tel.types.firstOrNull { it !in listOf(
                    VCardTelephoneType.PREF,
                    VCardTelephoneType.VOICE,
                    VCardTelephoneType.MSG,
                    VCardTelephoneType.TEXT,
                    VCardTelephoneType.VIDEO
                )}
                if (customType != null) {
                    PhoneType.CUSTOM
                } else {
                    PhoneType.OTHER
                }
            }
        }

        // Extract custom label if type is CUSTOM
        val label = if (type == PhoneType.CUSTOM) {
            tel.types.firstOrNull()?.value
        } else null

        return PhoneNumber(
            number = number,
            type = type,
            label = label
        )
    }

    /**
     * Parses a vCard Email to Email domain model.
     */
    private fun parseEmail(email: ezvcard.property.Email): Email {
        val emailAddress = email.value ?: ""

        // Map vCard email types to domain EmailType
        val type = when {
            email.types.contains(VCardEmailType.HOME) -> EmailType.HOME
            email.types.contains(VCardEmailType.WORK) -> EmailType.WORK
            else -> {
                // Check for custom type
                val customType = email.types.firstOrNull { it !in listOf(
                    VCardEmailType.PREF,
                    VCardEmailType.INTERNET
                )}
                if (customType != null) {
                    EmailType.CUSTOM
                } else {
                    EmailType.OTHER
                }
            }
        }

        // Extract custom label if type is CUSTOM
        val label = if (type == EmailType.CUSTOM) {
            email.types.firstOrNull()?.value
        } else null

        return Email(
            email = emailAddress,
            type = type,
            label = label
        )
    }

    /**
     * Parses a vCard Address to PostalAddress domain model.
     */
    private fun parsePostalAddress(addr: Address): PostalAddress {
        val street = addr.streetAddress ?: ""
        val city = addr.locality ?: ""
        val region = addr.region ?: ""
        val postalCode = addr.postalCode ?: ""
        val country = addr.country ?: ""

        // Map vCard address types to domain AddressType
        val type = when {
            addr.types.contains(VCardAddressType.HOME) -> AddressType.HOME
            addr.types.contains(VCardAddressType.WORK) -> AddressType.WORK
            else -> {
                // Check for custom type
                val customType = addr.types.firstOrNull { it !in listOf(
                    VCardAddressType.PREF,
                    VCardAddressType.DOM,
                    VCardAddressType.INTL,
                    VCardAddressType.POSTAL,
                    VCardAddressType.PARCEL
                )}
                if (customType != null) {
                    AddressType.CUSTOM
                } else {
                    AddressType.OTHER
                }
            }
        }

        // Extract custom label if type is CUSTOM
        val label = if (type == AddressType.CUSTOM) {
            addr.types.firstOrNull()?.value
        } else null

        return PostalAddress(
            street = street,
            city = city,
            region = region,
            postalCode = postalCode,
            country = country,
            type = type,
            label = label
        )
    }

    /**
     * Parses a vCard Birthday to ISO 8601 date string.
     */
    private fun parseBirthday(birthday: Birthday?): String? {
        if (birthday == null) return null

        return try {
            val date = birthday.date ?: return null
            val localDate = LocalDate.ofInstant(date.toInstant(), java.time.ZoneId.systemDefault())
            localDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parses a vCard Anniversary to ISO 8601 date string.
     */
    private fun parseAnniversary(anniversary: ezvcard.property.Anniversary?): String? {
        if (anniversary == null) return null

        return try {
            val date = anniversary.date ?: return null
            val localDate = LocalDate.ofInstant(date.toInstant(), java.time.ZoneId.systemDefault())
            localDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parses a vCard Photo to base64 encoded string.
     */
    private fun parsePhoto(photo: Photo?): String? {
        if (photo == null) return null

        return try {
            val data = photo.data ?: return null
            Base64.getEncoder().encodeToString(data)
        } catch (e: Exception) {
            null
        }
    }
}
