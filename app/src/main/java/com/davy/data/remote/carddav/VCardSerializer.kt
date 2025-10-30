package com.davy.data.remote.carddav

import com.davy.domain.model.AddressType
import com.davy.domain.model.Contact
import com.davy.domain.model.EmailType
import com.davy.domain.model.PhoneType
import ezvcard.Ezvcard
import ezvcard.VCard
import ezvcard.VCardVersion
import ezvcard.parameter.AddressType as VCardAddressType
import ezvcard.parameter.EmailType as VCardEmailType
import ezvcard.parameter.TelephoneType as VCardTelephoneType
import ezvcard.property.Address
import ezvcard.property.Anniversary
import ezvcard.property.Birthday
import ezvcard.property.Categories
import ezvcard.property.FormattedName
import ezvcard.property.Kind
import ezvcard.property.Member
import ezvcard.property.Nickname
import ezvcard.property.Note
import ezvcard.property.Organization
import ezvcard.property.Photo
import ezvcard.property.StructuredName
import ezvcard.property.Telephone
import ezvcard.property.Title
import ezvcard.property.Uid
import ezvcard.property.Url
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * vCard serializer using ez-vcard library.
 * Serializes Contact domain model to vCard 3.0 and 4.0 formats.
 *
 * Serialized vCard properties:
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
 * - UID: Unique identifier
 */
@Singleton
class VCardSerializer @Inject constructor() {

    /**
     * Serializes a Contact domain model to vCard string.
     *
     * @param contact The contact to serialize
     * @param version The vCard version to use (default: 3.0)
     * @return vCard data as a string
     */
    fun serialize(contact: Contact, version: VCardVersion = VCardVersion.V3_0): String {
        val vcard = VCard()

        // Set UID (required)
        vcard.uid = Uid(contact.uid)

        // Set formatted name (required)
        vcard.formattedName = FormattedName(contact.displayName)

        // Set structured name
        if (contact.givenName != null || contact.familyName != null) {
            val structuredName = StructuredName()
            structuredName.given = contact.givenName
            structuredName.family = contact.familyName
            if (contact.middleName != null) {
                structuredName.additionalNames.add(contact.middleName)
            }
            if (contact.namePrefix != null) {
                structuredName.prefixes.add(contact.namePrefix)
            }
            if (contact.nameSuffix != null) {
                structuredName.suffixes.add(contact.nameSuffix)
            }
            vcard.structuredName = structuredName
        }

        // Set nickname
        if (contact.nickname != null) {
            val nickname = Nickname()
            nickname.values.add(contact.nickname)
            vcard.nickname = nickname
        }

        // Set organization
        if (contact.organization != null) {
            val org = Organization()
            org.values.add(contact.organization)
            if (contact.organizationUnit != null) {
                org.values.add(contact.organizationUnit)
            }
            vcard.organization = org
        }

        // Set job title
        if (contact.jobTitle != null) {
            vcard.addTitle(contact.jobTitle)
        }

        // Set phone numbers
        contact.phoneNumbers.forEach { phone ->
            val tel = Telephone(phone.number)
            
            // Map domain PhoneType to vCard telephone types
            when (phone.type) {
                PhoneType.MOBILE -> tel.types.add(VCardTelephoneType.CELL)
                PhoneType.HOME -> tel.types.add(VCardTelephoneType.HOME)
                PhoneType.WORK -> tel.types.add(VCardTelephoneType.WORK)
                PhoneType.FAX_HOME -> {
                    tel.types.add(VCardTelephoneType.FAX)
                    tel.types.add(VCardTelephoneType.HOME)
                }
                PhoneType.FAX_WORK -> {
                    tel.types.add(VCardTelephoneType.FAX)
                    tel.types.add(VCardTelephoneType.WORK)
                }
                PhoneType.PAGER -> tel.types.add(VCardTelephoneType.PAGER)
                PhoneType.CUSTOM -> {
                    if (phone.label != null) {
                        tel.types.add(VCardTelephoneType.get(phone.label))
                    }
                }
                PhoneType.OTHER -> {
                    // No specific type
                }
            }

            vcard.addTelephoneNumber(tel)
        }

        // Set emails
        contact.emails.forEach { email ->
            val emailProp = ezvcard.property.Email(email.email)
            
            // Map domain EmailType to vCard email types
            when (email.type) {
                EmailType.HOME -> emailProp.types.add(VCardEmailType.HOME)
                EmailType.WORK -> emailProp.types.add(VCardEmailType.WORK)
                EmailType.CUSTOM -> {
                    if (email.label != null) {
                        emailProp.types.add(VCardEmailType.get(email.label))
                    }
                }
                EmailType.OTHER -> {
                    // No specific type
                }
            }

            vcard.addEmail(emailProp)
        }

        // Set postal addresses
        contact.postalAddresses.forEach { addr ->
            val address = Address()
            address.streetAddress = addr.street
            address.locality = addr.city
            address.region = addr.region
            address.postalCode = addr.postalCode
            address.country = addr.country

            // Map domain AddressType to vCard address types
            when (addr.type) {
                AddressType.HOME -> address.types.add(VCardAddressType.HOME)
                AddressType.WORK -> address.types.add(VCardAddressType.WORK)
                AddressType.CUSTOM -> {
                    if (addr.label != null) {
                        address.types.add(VCardAddressType.get(addr.label))
                    }
                }
                AddressType.OTHER -> {
                    // No specific type
                }
            }

            vcard.addAddress(address)
        }

        // Set websites
        contact.websites.forEach { website ->
            vcard.addUrl(website)
        }

        // Set note
        if (contact.note != null) {
            vcard.addNote(contact.note)
        }

        // Set birthday
        if (contact.birthday != null) {
            val birthday = parseDateString(contact.birthday)
            if (birthday != null) {
                vcard.birthday = Birthday(birthday)
            }
        }

        // Set anniversary
        if (contact.anniversary != null) {
            val anniversary = parseDateString(contact.anniversary)
            if (anniversary != null) {
                vcard.anniversary = Anniversary(anniversary)
            }
        }

        // Set photo
        if (contact.photoBase64 != null) {
            try {
                val photoData = Base64.getDecoder().decode(contact.photoBase64)
                val photo = Photo(photoData, ezvcard.parameter.ImageType.JPEG)
                vcard.addPhoto(photo)
            } catch (e: Exception) {
                // Ignore invalid photo data
                e.printStackTrace()
            }
        }

        // Set group support
        if (contact.categories.isNotEmpty()) {
            val categories = Categories()
            categories.values.addAll(contact.categories)
            vcard.categories = categories
        }
        
        if (contact.isGroup && version == VCardVersion.V4_0) {
            // KIND:group only available in vCard 4.0
            vcard.kind = Kind.group()
            
            // Add members
            contact.groupMembers.forEach { memberUid ->
                vcard.addMember(Member("urn:uuid:$memberUid"))
            }
        }

        // Serialize to string
        return Ezvcard.write(vcard).version(version).go()
    }

    /**
     * Serializes a Contact to vCard 3.0 format.
     *
     * vCard 3.0 is the most widely supported format.
     *
     * @param contact The contact to serialize
     * @return vCard 3.0 data as a string
     */
    fun serializeV3(contact: Contact): String {
        return serialize(contact, VCardVersion.V3_0)
    }

    /**
     * Serializes a Contact to vCard 4.0 format.
     *
     * vCard 4.0 has better support for modern features.
     *
     * @param contact The contact to serialize
     * @return vCard 4.0 data as a string
     */
    fun serializeV4(contact: Contact): String {
        return serialize(contact, VCardVersion.V4_0)
    }

    /**
     * Parses an ISO 8601 date string to Date.
     */
    private fun parseDateString(dateString: String): Date? {
        return try {
            val localDate = LocalDate.parse(dateString, DateTimeFormatter.ISO_LOCAL_DATE)
            Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant())
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Determines the appropriate vCard version based on server support.
     *
     * @param supportedVersions List of supported vCard versions (e.g., ["3.0", "4.0"])
     * @return VCardVersion to use (defaults to 3.0 for maximum compatibility)
     */
    fun determineVersion(supportedVersions: List<String>): VCardVersion {
        return when {
            supportedVersions.contains("4.0") -> VCardVersion.V4_0
            supportedVersions.contains("3.0") -> VCardVersion.V3_0
            else -> VCardVersion.V3_0 // Default to 3.0 for compatibility
        }
    }

    /**
     * Validates a serialized vCard string.
     *
     * @param vcardData The vCard data to validate
     * @return True if valid, false otherwise
     */
    fun isValid(vcardData: String): Boolean {
        return try {
            val vcard = Ezvcard.parse(vcardData).first()
            vcard != null && vcard.uid != null && vcard.formattedName != null
        } catch (e: Exception) {
            false
        }
    }
}
