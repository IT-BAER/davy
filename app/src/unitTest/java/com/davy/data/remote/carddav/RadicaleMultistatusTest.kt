package com.davy.data.remote.carddav

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Synology Contacts serves CardDAV through Radicale, which writes DAV: as the default
 * namespace (no prefix) and the CardDAV namespace as CR:. Bodies are captured from
 * Synology Contacts 1.0.5-10492 (Radicale 2.1.10) answering DAVy's own requests.
 */
class RadicaleMultistatusTest {

    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `parses a Synology addressbook-query response with vcard data`() {
        val contacts = AddressBookQuery().parseQueryResponse(SYNOLOGY_QUERY_ALL)

        assertThat(contacts.map { it.url }).containsExactly(HREF_1, HREF_2)
        assertThat(contacts.map { it.etag }).containsExactly(ETAG_1, ETAG_2)
        assertThat(contacts[0].vcardData).contains("FN:Testkontakt 1")
    }

    @Test
    fun `lists a Synology collection instead of reporting it empty`() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(207).setBody(SYNOLOGY_ETAG_ONLY))
        server.enqueue(MockResponse().setResponseCode(207).setBody(SYNOLOGY_QUERY_ALL))

        val entries = AddressBookEtagLister(OkHttpClient.Builder().build())
            .list(server.url("/carddav/davytest/repro/").toString(), "davytest", "secret")

        assertThat(entries).isNotNull()
        assertThat(entries!!.map { it.url }).containsExactly(HREF_1, HREF_2)
    }

    @Test
    fun `resolves a Synology href against the address book host`() {
        val url = ContactPut.resolveContactUrl("https://nas.example:5001/carddav/davytest/repro/", HREF_1)

        assertThat(url).isEqualTo("https://nas.example:5001/carddav/davytest/repro/repro-1.vcf")
    }

    @Test
    fun `keeps the Nextcloud href join unchanged`() {
        val url = ContactPut.resolveContactUrl(
            "https://cloud.example/remote.php/dav/addressbooks/users/u/contacts/",
            "/remote.php/dav/addressbooks/users/u/contacts/a.vcf"
        )

        assertThat(url).isEqualTo("https://cloud.example/remote.php/dav/addressbooks/users/u/contacts/a.vcf")
    }

    private companion object {
        const val HREF_1 = "/carddav/davytest/repro/repro-1.vcf"
        const val HREF_2 = "/carddav/davytest/repro/repro-2.vcf"
        const val ETAG_1 = "6aa3e2b0a6d0c32302a91bf0797049a8"
        const val ETAG_2 = "337e3dc56418dfe6ec331b8362e9d172"

        const val SYNOLOGY_ETAG_ONLY = "<?xml version='1.0' encoding='utf-8'?>\n" +
            "<multistatus xmlns=\"DAV:\">" +
            "<response><href>$HREF_1</href><propstat><prop><getetag>\"$ETAG_1\"</getetag></prop>" +
            "<status>HTTP/1.1 200 OK</status></propstat></response>" +
            "<response><href>$HREF_2</href><propstat><prop><getetag>\"$ETAG_2\"</getetag></prop>" +
            "<status>HTTP/1.1 200 OK</status></propstat></response></multistatus>"

        const val SYNOLOGY_QUERY_ALL = "<?xml version='1.0' encoding='utf-8'?>\n" +
            "<multistatus xmlns=\"DAV:\" xmlns:CR=\"urn:ietf:params:xml:ns:carddav\">" +
            "<response><href>$HREF_1</href><propstat><prop><getetag>\"$ETAG_1\"</getetag>" +
            "<CR:address-data>BEGIN:VCARD\nVERSION:3.0\nUID:repro-1\nFN:Testkontakt 1\n" +
            "N:1;Testkontakt;;;\nTEL;TYPE=CELL:+43 660 0000001\nEND:VCARD\n</CR:address-data>" +
            "</prop><status>HTTP/1.1 200 OK</status></propstat></response>" +
            "<response><href>$HREF_2</href><propstat><prop><getetag>\"$ETAG_2\"</getetag>" +
            "<CR:address-data>BEGIN:VCARD\nVERSION:3.0\nUID:repro-2\nFN:Testkontakt 2\n" +
            "N:2;Testkontakt;;;\nTEL;TYPE=CELL:+43 660 0000002\nEND:VCARD\n</CR:address-data>" +
            "</prop><status>HTTP/1.1 200 OK</status></propstat></response></multistatus>"
    }
}
