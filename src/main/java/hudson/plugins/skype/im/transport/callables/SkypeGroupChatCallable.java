/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package hudson.plugins.skype.im.transport.callables;

import hudson.plugins.skype.im.transport.SkypeIMException;

import java.util.logging.Logger;

import com.skype.Chat;
import com.skype.Group;
import com.skype.Skype;
import com.skype.SkypeException;
import com.skype.connector.Connector;
import com.skype.connector.ConnectorException;

/**
 *
 * @author jbh
 */
public class SkypeGroupChatCallable extends SkypeChatCallable {
    private String chatName = null;

    public SkypeGroupChatCallable(String chatName, String msg) {
        super(null, msg);
        this.chatName = chatName;

    }
    @Override
    public Void call() throws SkypeIMException {
        try {
            Group group = Skype.getContactList().getGroup(chatName);
            Chat[] chats = Skype.getAllChats();
            Chat useChat = null;
            for (Chat chat : chats) {
                String topic = getProperty("CHAT", chat.getId(), "TOPIC");
                if (topic != null && topic.contains(chatName)) {
                    useChat = chat;
                    break;
                }
            }
            if (useChat == null && group != null) {
                useChat = Skype.chat("");
                useChat.setTopic(chatName);
                useChat.addUsers(group.getAllFriends());
            } else if (useChat == null) {
                throw new SkypeIMException("Could not find group/category/chat "+chatName);
            }
            useChat.send(message);
            return null;
        } catch (SkypeException ex) {
            throw new SkypeIMException(ex);
        }
    }

    private String getProperty(String type, String id, String name) throws SkypeException {
        try {
            String command = "GET " + type + " " + id + " " + name;
            String responseHeader = type + " " + id + " " + name + " ";
            String response = Connector.getInstance().execute(command, responseHeader);
            return response.substring((responseHeader).length());
        } catch (ConnectorException e) {
            Logger.getLogger(this.getClass().getName()).severe(e.getLocalizedMessage());
            return null;
        }
    }
}
