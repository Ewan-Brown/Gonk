package org.ewan.mcviewer

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.requests.GatewayIntent

fun main(token : Array<String>) {;

    var api: JDA

    val introRole = 1355324621032784013
    val staffRole = 1037777604448632855
    val introChannel = "introductions"
    val eventsChannel = "events"

    while(true) {
        try{
            api = JDABuilder.createDefault(token[0])
                .setEventPassthrough(true)
                .enableIntents(GatewayIntent.MESSAGE_CONTENT) //Make sure this is enabled in developer menu for bot :)
                .build()
            break
        }catch (e: ErrorResponseException){
            System.err.println("Failed to connect to network, trying again in 5s")
            Thread.sleep(5000)
        }
    }
    println("Successfully connected")
    api.addEventListener(TestListener(introRole, staffRole, introChannel, eventsChannel))

}