package com.davy.data.remote.carddav

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

/**
 * CardDAV service interface for contact operations.
 * 
 * Implements CardDAV protocol (RFC 6352) operations for:
 * - Address book discovery (PROPFIND)
 * - Contact synchronization (REPORT)
 * - Contact CRUD operations (GET, PUT, DELETE)
 */
interface CardDAVService {
    
    /**
     * PROPFIND request to discover address books and their properties.
     * 
     * @param url The CardDAV endpoint URL
     * @param depth The depth header (0, 1, or infinity)
     * @param body The PROPFIND request body
     */
    @HTTP(method = "PROPFIND", hasBody = true)
    suspend fun propfind(
        @Url url: String,
        @Header("Depth") depth: String = "1",
        @Body body: RequestBody
    ): Response<ResponseBody>
    
    /**
     * REPORT request for address book queries and synchronization.
     * 
     * @param url The address book URL
     * @param body The REPORT request body
     */
    @HTTP(method = "REPORT", hasBody = true)
    suspend fun report(
        @Url url: String,
        @Body body: RequestBody
    ): Response<ResponseBody>
    
    /**
     * GET request to retrieve a contact (vCard).
     * 
     * @param url The contact URL
     */
    @GET
    suspend fun getContact(
        @Url url: String
    ): Response<ResponseBody>
    
    /**
     * PUT request to create or update a contact.
     * 
     * @param url The contact URL
     * @param ifMatch The If-Match header for conflict detection
     * @param body The vCard data
     */
    @PUT
    suspend fun putContact(
        @Url url: String,
        @Header("If-Match") ifMatch: String? = null,
        @Body body: RequestBody
    ): Response<ResponseBody>
    
    /**
     * DELETE request to remove a contact.
     * 
     * @param url The contact URL
     * @param ifMatch The If-Match header for conflict detection
     */
    @DELETE
    suspend fun deleteContact(
        @Url url: String,
        @Header("If-Match") ifMatch: String? = null
    ): Response<ResponseBody>
    
    /**
     * OPTIONS request to discover server capabilities.
     * 
     * @param url The CardDAV endpoint URL
     */
    @OPTIONS
    suspend fun options(
        @Url url: String
    ): Response<ResponseBody>
}

/**
 * Request body wrapper for Retrofit.
 */
typealias RequestBody = okhttp3.RequestBody
