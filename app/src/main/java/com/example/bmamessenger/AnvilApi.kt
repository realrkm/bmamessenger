package com.example.bmamessenger

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Streaming

/**
 * Defines the API endpoints for interacting with the Anvil service.
 * This interface is used by Retrofit to make network requests.
 */
interface AnvilApi {
    /**
     * Retrieves a list of pending SMS messages from the Anvil API.
     *
     * @return A list of [SmsMessage] objects representing the pending SMS messages.
     */
    @GET("_/api/pending-sms")
    suspend fun getPendingSms(): List<SmsMessage>
    
    /**
     * Marks a specific SMS message as sent in the Anvil API.
     *
     * @param id The unique identifier of the SMS message to mark as sent.
     */
    @POST("_/api/mark-sent/{id}")
    suspend fun markAsSent(@Path("id") id: Int)

    /**
     * Generates a PDF document for a given job card ID.
     *
     * @param jobCardId The unique identifier of the job card.
     * @return A [ResponseBody] containing the PDF file.
     */
    @Streaming
    @GET("_/api/generate-pdf/{jobcardid}")
    suspend fun generatePdf(@Path("jobcardid") jobCardId: Int): ResponseBody
}
