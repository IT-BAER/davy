package com.davy.data.contentprovider

import android.provider.ContactsContract
import com.davy.domain.model.AddressType
import com.davy.domain.model.EmailType
import com.davy.domain.model.PhoneType
import javax.inject.Inject

/**
 * Type mapper for converting between domain model types and Android ContactsContract types.
 *
 * This class handles bidirectional mapping for:
 * - Phone number types (PhoneType <-> ContactsContract.CommonDataKinds.Phone.TYPE_*)
 * - Email types (EmailType <-> ContactsContract.CommonDataKinds.Email.TYPE_*)
 * - Address types (AddressType <-> ContactsContract.CommonDataKinds.StructuredPostal.TYPE_*)
 */
class TypeMapper @Inject constructor() {

    // --- Phone Type Mapping ---

    /**
     * Maps domain PhoneType to Android ContactsContract phone type constant.
     *
     * @param phoneType The domain phone type
     * @return Android phone type constant
     */
    fun phoneTypeToAndroid(phoneType: PhoneType): Int {
        return when (phoneType) {
            PhoneType.MOBILE -> ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
            PhoneType.HOME -> ContactsContract.CommonDataKinds.Phone.TYPE_HOME
            PhoneType.WORK -> ContactsContract.CommonDataKinds.Phone.TYPE_WORK
            PhoneType.FAX_HOME -> ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME
            PhoneType.FAX_WORK -> ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK
            PhoneType.PAGER -> ContactsContract.CommonDataKinds.Phone.TYPE_PAGER
            PhoneType.OTHER -> ContactsContract.CommonDataKinds.Phone.TYPE_OTHER
            PhoneType.CUSTOM -> ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM
        }
    }

    /**
     * Maps Android ContactsContract phone type to domain PhoneType.
     *
     * @param androidType The Android phone type constant
     * @return Domain PhoneType
     */
    fun androidToPhoneType(androidType: Int): PhoneType {
        return when (androidType) {
            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> PhoneType.MOBILE
            ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> PhoneType.HOME
            ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> PhoneType.WORK
            ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME -> PhoneType.FAX_HOME
            ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK -> PhoneType.FAX_WORK
            ContactsContract.CommonDataKinds.Phone.TYPE_PAGER -> PhoneType.PAGER
            ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM -> PhoneType.CUSTOM
            else -> PhoneType.OTHER
        }
    }

    // --- Email Type Mapping ---

    /**
     * Maps domain EmailType to Android ContactsContract email type constant.
     *
     * @param emailType The domain email type
     * @return Android email type constant
     */
    fun emailTypeToAndroid(emailType: EmailType): Int {
        return when (emailType) {
            EmailType.HOME -> ContactsContract.CommonDataKinds.Email.TYPE_HOME
            EmailType.WORK -> ContactsContract.CommonDataKinds.Email.TYPE_WORK
            EmailType.OTHER -> ContactsContract.CommonDataKinds.Email.TYPE_OTHER
            EmailType.CUSTOM -> ContactsContract.CommonDataKinds.Email.TYPE_CUSTOM
        }
    }

    /**
     * Maps Android ContactsContract email type to domain EmailType.
     *
     * @param androidType The Android email type constant
     * @return Domain EmailType
     */
    fun androidToEmailType(androidType: Int): EmailType {
        return when (androidType) {
            ContactsContract.CommonDataKinds.Email.TYPE_HOME -> EmailType.HOME
            ContactsContract.CommonDataKinds.Email.TYPE_WORK -> EmailType.WORK
            ContactsContract.CommonDataKinds.Email.TYPE_CUSTOM -> EmailType.CUSTOM
            else -> EmailType.OTHER
        }
    }

    // --- Address Type Mapping ---

    /**
     * Maps domain AddressType to Android ContactsContract address type constant.
     *
     * @param addressType The domain address type
     * @return Android address type constant
     */
    fun addressTypeToAndroid(addressType: AddressType): Int {
        return when (addressType) {
            AddressType.HOME -> ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME
            AddressType.WORK -> ContactsContract.CommonDataKinds.StructuredPostal.TYPE_WORK
            AddressType.OTHER -> ContactsContract.CommonDataKinds.StructuredPostal.TYPE_OTHER
            AddressType.CUSTOM -> ContactsContract.CommonDataKinds.StructuredPostal.TYPE_CUSTOM
        }
    }

    /**
     * Maps Android ContactsContract address type to domain AddressType.
     *
     * @param androidType The Android address type constant
     * @return Domain AddressType
     */
    fun androidToAddressType(androidType: Int): AddressType {
        return when (androidType) {
            ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME -> AddressType.HOME
            ContactsContract.CommonDataKinds.StructuredPostal.TYPE_WORK -> AddressType.WORK
            ContactsContract.CommonDataKinds.StructuredPostal.TYPE_CUSTOM -> AddressType.CUSTOM
            else -> AddressType.OTHER
        }
    }
}
