/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package hudson.plugins.skype.im.transport.callables;

import hudson.plugins.skype.im.transport.SkypeIMException;

import com.skype.Chat;
import com.skype.ChatMessage;
import com.skype.Group;
import com.skype.Skype;
import com.skype.SkypeException;

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
    public ChatMessage call() throws SkypeIMException {
        try {
            Group group = Skype.getContactList().getGroup(chatName);
            Chat[] chats = Skype.getAllChats();
            Chat useChat = null;
            for (Chat chat : chats) {
                if (chat.getWindowTitle().contains(chatName)) {
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
            return useChat.send(message);

        } catch (SkypeException ex) {
            throw new SkypeIMException(ex);
        }
    }

}
