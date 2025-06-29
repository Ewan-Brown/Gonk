package org.ewan.mcviewer

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.message.react.GenericMessageReactionEvent
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction

class Gonk(private val introRole: Long, private val staffRole: Long, private val introChannelName: String, private val eventsForumName: String) : ListenerAdapter() {

    override fun onMessageReceived(event: MessageReceivedEvent) {
        println("Message received, created at ${event.message.timeCreated}")
        if (event.author.isBot || !event.channel.name.contains(introChannelName)) {
            println("Message was bot or in wrong channel, returning")
            return
        }
        println("adding intro role to author!")
        event.guild.addRoleToMember(event.author, event.guild.getRoleById(introRole)!!).queue({
            println("Successfully added intro role to author!")
        },
        {
            println("failed to add intro role to author")
            it.printStackTrace()
        })
    }

    private fun process(event: GenericMessageReactionEvent, processSuccess: (GenericMessageReactionEvent) -> Unit) : Unit{
        if (event.channel is ThreadChannel) {
            val forumName = event.channel.asThreadChannel().parentChannel.name
            if(forumName.equals(eventsForumName)){
                event.channel.asThreadChannel().retrieveStartMessage().queue({
                    if(it.idLong == event.messageIdLong){
                        println("identified as start message")
                        processSuccess(event)
                    }
                }, {
                    println("failed to retrieve start message")
                    it.printStackTrace()
                })
            }
        }
    }

    enum class Modifications(val a: (Guild, Member, Role) -> AuditableRestAction<Void>) {
        ADD({ guild, member, role -> guild.addRoleToMember(member, role)}),
        REMOVE({guild, member, role -> guild.removeRoleFromMember(member, role)})
    }

    private fun safelyModifyRole(role: Role, event: GenericMessageReactionEvent, member: Member, modifications: Modifications) {
        modifications.a(event.guild, member, role).queue({
            println("successfully ${modifications.name} role ${role.name} for user ${member.idLong}")
        }, {
            println("failed to ${modifications.name} role ${role.name} for user ${member.idLong}")
            throw it
        })
    }

    val onAdd : (GenericMessageReactionEvent) -> Unit = {
            event ->
        println("Identified as forum reaction! Checking if role exists for this event yet")
        val channelName = event.channel.name
        val matchingRoles = event.guild.getRolesByName(channelName, false)
        event.guild.retrieveMemberById(event.userId).queue(
            { member ->
                println("Matching roles: $matchingRoles")
                if (matchingRoles.isEmpty()) {
                    println("Creating role [$channelName]")
                    event.guild.createRole().setName(channelName).setMentionable(true).queue({
                        println("succesfully created role ${channelName}")
                        safelyModifyRole(it, event, member, Modifications.ADD)
                    }, {
                        println("failed to create role [$channelName]")
                        it.printStackTrace()
                    })
                } else {
                    val role = matchingRoles[0]
                    println("Matching role found : ${role}")
                    if(!member.roles.contains(role)){
                        println("user was not part of role, adding it!!")
                        safelyModifyRole(role, event, member, Modifications.ADD)
                    }else{
                        println("user is already part of role!")
                    }
                }
            }, {
                println("failed to retrieve member with id : ${event.userId}")
                it.printStackTrace()
        })
    }

    val onRemove: (GenericMessageReactionEvent) -> Unit = {
            event ->
        println("Identified as forum reaction!")
        val channelName = event.channel.name
        val matchingRoles = event.guild.getRolesByName(channelName, false)
        event.guild.retrieveMemberById(event.userId).queue(
            { member ->
                println("Matching roles: $matchingRoles")
                if (matchingRoles.isEmpty()) {
                    println("No matching role found. Exiting")
                } else {
                    val role = matchingRoles[0]
                    println("Matching role found : ${role}")
                    if (member.roles.contains(role)) {
                        safelyModifyRole(role, event, member, Modifications.REMOVE)
                    } else {
                        println("user not part of role!")
                    }
                }
            }, {
                println("failed to retrieve member with id : ${event.userId}")
                it.printStackTrace()
            })
    }

    override fun onMessageReactionAdd(event: MessageReactionAddEvent) {
        println()
        println("Message reaction add detected!")
        process(event, onAdd)
    }

    override fun onMessageReactionRemove(event: MessageReactionRemoveEvent) {
        println()
        println("Message reaction rem detected!")
        process(event, onRemove)
    }
}