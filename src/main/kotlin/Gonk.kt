package org.ewan.mcviewer

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.message.react.GenericMessageReactionEvent
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction

class Gonk(val api: JDA, private val introRole: Long, private val doRetroactiveProcessing: Boolean, private val introChannelName: String, private val forumsToScan: List<String>) : ListenerAdapter() {

    override fun onReady(event: ReadyEvent) {
        println("Ready!!!")
        super.onReady(event)
        if(doRetroactiveProcessing) {
            api.guilds.forEach { guild ->
                forumsToScan.forEach { forumName ->
                    val matchingForums = guild.getForumChannelsByName(forumName, false)
                    if (matchingForums.size > 0) {
                        val channels = matchingForums[0].threadChannels
                        println("Forum discovered: " + matchingForums[0])
                        channels.forEach { channel ->
                            println("Forum post discovered: " + channel?.name)
                            val channelName = channel!!.name

                            val matchingRoles = guild.getRolesByName(channelName, false)
                            println(matchingRoles)
                            val addRoles: () -> Unit = {
                                println("adding roles to correct users")
                                api.getThreadChannelById(channel.idLong)!!.retrieveStartMessage().queue {
                                    for (reaction in it.reactions) {
                                        reaction.retrieveUsers().queue { users ->
                                            users.forEach { user ->
                                                guild.retrieveMemberById(user.idLong).queue { member ->
                                                    safelyModifyRole(matchingRoles[0], guild, member, Modifications.ADD)
                                                }
                                            }
                                        }
                                    }
                                }
                            }


                            if (matchingRoles.isEmpty()) {
                                println("Creating role [$channelName]")
                                guild.createRole().setName(channelName).setMentionable(true).queue {
                                    println("successfully created role $channelName")
                                    addRoles()
                                }
                            } else {
                                println("clearing existing users!")
                                guild.members.forEach { member ->
                                    safelyModifyRole(
                                        matchingRoles[0],
                                        guild,
                                        member,
                                        Modifications.REMOVE
                                    )
                                }
                                println("done clearing!")
                                addRoles()
                            }

                        }
                    } else {
                        println("forum not found : $forumName")
                    }
                }

            }
        }
        println("done readying")
    }

    override fun onChannelDelete(event: ChannelDeleteEvent) {
        super.onChannelDelete(event)
        val matchingRoles = event.guild.getRolesByName(event.channel.name, false)
        if(matchingRoles.size > 0) {
            println("channel deleted, removing role ${matchingRoles[0]}")
            event.guild.members.forEach { member ->
                safelyModifyRole(
                    matchingRoles[0],
                    event.guild,
                    member,
                    Modifications.REMOVE
                )
            }
            println("deleting role ${matchingRoles[0]}")
            matchingRoles[0].delete().queue({ println("succesfully deleted role ") })
        }
    }

    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (event.author.isBot || !event.channel.name.contains(introChannelName)) {
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
            if(forumsToScan.contains(forumName)) {
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

    private fun safelyModifyRole(role: Role, guild: Guild, member: Member, modifications: Modifications) {
        modifications.a(guild, member, role).queue({
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
                        safelyModifyRole(it, event.guild, member, Modifications.ADD)
                    }, {
                        println("failed to create role [$channelName]")
                        it.printStackTrace()
                    })
                } else {
                    val role = matchingRoles[0]
                    println("Matching role found : ${role}")
                    if(!member.roles.contains(role)){
                        println("user was not part of role, adding it!!")
                        safelyModifyRole(role, event.guild, member, Modifications.ADD)
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
                        safelyModifyRole(role, event.guild, member, Modifications.REMOVE)
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
        process(event, onAdd)
    }

    override fun onMessageReactionRemove(event: MessageReactionRemoveEvent) {
        process(event, onRemove)
    }
}