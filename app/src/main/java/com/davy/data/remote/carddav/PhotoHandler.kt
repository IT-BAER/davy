package com.davy.data.remote.carddav

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.util.Base64

/**
 * Photo handler for encoding and decoding contact photos.
 *
 * Provides utilities for:
 * - Encoding Bitmap to base64 string
 * - Decoding base64 string to Bitmap
 * - Resizing photos to reduce storage size
 * - Compressing photos to JPEG format
 */
object PhotoHandler {

    /**
     * Default maximum photo dimension (width or height).
     * Photos larger than this will be scaled down.
     */
    const val DEFAULT_MAX_DIMENSION = 512

    /**
     * Default JPEG quality (0-100).
     * Higher values mean better quality but larger file size.
     */
    const val DEFAULT_JPEG_QUALITY = 85

    /**
     * Encodes a Bitmap to a base64 string.
     *
     * The bitmap is compressed to JPEG format before encoding.
     *
     * @param bitmap The bitmap to encode
     * @param maxDimension Maximum width or height (larger dimension will be scaled)
     * @param quality JPEG quality (0-100)
     * @return Base64 encoded string, or null if encoding fails
     */
    fun encodePhoto(
        bitmap: Bitmap,
        maxDimension: Int = DEFAULT_MAX_DIMENSION,
        quality: Int = DEFAULT_JPEG_QUALITY
    ): String? {
        return try {
            // Resize if needed
            val resized = resizeBitmap(bitmap, maxDimension)

            // Compress to JPEG
            val outputStream = ByteArrayOutputStream()
            resized.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            val bytes = outputStream.toByteArray()

            // Encode to base64
            Base64.getEncoder().encodeToString(bytes)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Decodes a base64 string to a Bitmap.
     *
     * @param base64String The base64 encoded photo data
     * @return Bitmap, or null if decoding fails
     */
    fun decodePhoto(base64String: String): Bitmap? {
        return try {
            // Decode from base64
            val bytes = Base64.getDecoder().decode(base64String)

            // Decode to bitmap
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Encodes a byte array (photo data) to a base64 string.
     *
     * This is useful when the photo is already in JPEG or PNG format.
     *
     * @param photoData The photo data as byte array
     * @return Base64 encoded string
     */
    fun encodePhotoData(photoData: ByteArray): String {
        return Base64.getEncoder().encodeToString(photoData)
    }

    /**
     * Decodes a base64 string to a byte array (photo data).
     *
     * @param base64String The base64 encoded photo data
     * @return Photo data as byte array, or null if decoding fails
     */
    fun decodePhotoData(base64String: String): ByteArray? {
        return try {
            Base64.getDecoder().decode(base64String)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Resizes a bitmap to fit within the specified maximum dimension.
     *
     * The aspect ratio is preserved.
     *
     * @param bitmap The bitmap to resize
     * @param maxDimension Maximum width or height
     * @return Resized bitmap (or original if already smaller)
     */
    fun resizeBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        // Check if resizing is needed
        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }

        // Calculate new dimensions
        val aspectRatio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int

        if (width > height) {
            // Landscape
            newWidth = maxDimension
            newHeight = (maxDimension / aspectRatio).toInt()
        } else {
            // Portrait or square
            newHeight = maxDimension
            newWidth = (maxDimension * aspectRatio).toInt()
        }

        // Resize
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Validates that a base64 string contains valid photo data.
     *
     * Performs basic validation:
     * - Not empty
     * - Valid base64 encoding
     * - Can be decoded to a bitmap
     *
     * @param base64String The base64 encoded photo data
     * @return True if valid, false otherwise
     */
    fun isValidPhoto(base64String: String): Boolean {
        if (base64String.isBlank()) {
            return false
        }

        // Try to decode
        val bitmap = decodePhoto(base64String)
        return bitmap != null
    }

    /**
     * Gets the size of the photo data in bytes.
     *
     * @param base64String The base64 encoded photo data
     * @return Size in bytes, or 0 if invalid
     */
    fun getPhotoSize(base64String: String): Int {
        val photoData = decodePhotoData(base64String) ?: return 0
        return photoData.size
    }

    /**
     * Compresses a photo to reduce file size.
     *
     * This is useful for reducing storage and bandwidth usage.
     *
     * @param base64String The base64 encoded photo data
     * @param maxDimension Maximum width or height
     * @param quality JPEG quality (0-100)
     * @return Compressed photo as base64 string, or null if compression fails
     */
    fun compressPhoto(
        base64String: String,
        maxDimension: Int = DEFAULT_MAX_DIMENSION,
        quality: Int = DEFAULT_JPEG_QUALITY
    ): String? {
        val bitmap = decodePhoto(base64String) ?: return null
        return encodePhoto(bitmap, maxDimension, quality)
    }

    /**
     * Determines the format of the photo data.
     *
     * @param photoData The photo data as byte array
     * @return Format string ("JPEG", "PNG", "GIF", "UNKNOWN")
     */
    fun getPhotoFormat(photoData: ByteArray): String {
        if (photoData.isEmpty()) {
            return "UNKNOWN"
        }

        // Check JPEG magic number (FF D8 FF)
        if (photoData.size >= 3 &&
            photoData[0] == 0xFF.toByte() &&
            photoData[1] == 0xD8.toByte() &&
            photoData[2] == 0xFF.toByte()) {
            return "JPEG"
        }

        // Check PNG magic number (89 50 4E 47)
        if (photoData.size >= 4 &&
            photoData[0] == 0x89.toByte() &&
            photoData[1] == 0x50.toByte() &&
            photoData[2] == 0x4E.toByte() &&
            photoData[3] == 0x47.toByte()) {
            return "PNG"
        }

        // Check GIF magic number (47 49 46)
        if (photoData.size >= 3 &&
            photoData[0] == 0x47.toByte() &&
            photoData[1] == 0x49.toByte() &&
            photoData[2] == 0x46.toByte()) {
            return "GIF"
        }

        return "UNKNOWN"
    }
}
