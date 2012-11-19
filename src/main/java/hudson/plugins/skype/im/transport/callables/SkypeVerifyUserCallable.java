/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.skype.im.transport.callables;

import hudson.plugins.skype.im.transport.SkypeIMException;
import hudson.remoting.Callable;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.skype.Skype;
import com.skype.SkypeException;
import com.skype.User;
import com.skype.User.BuddyStatus;

/**
 *
 * @author jbh
 */
public class SkypeVerifyUserCallable implements Callable<String, SkypeIMException> {
    private static final Logger LOGGER = Logger.getLogger(SkypeVerifyUserCallable.class.getName());

    private String skypeNames = null;

    public SkypeVerifyUserCallable(String names) {
        this.skypeNames = names;
    }

    public String call() throws SkypeIMException {
        String result = null;

        User usr = Skype.getUser(skypeNames);

        try {
            if (usr == null || usr.getFullName() == null || usr.getFullName().trim().length() <= 0) {
                usr = null;
                User[] users = Skype.searchUsers(skypeNames);
                if (skypeNames != null && skypeNames.contains("@")) {
                        //EMail, so this must be ok.
                    usr = users[0];
                } else {
                    for (User user : users) {
                        if (user.getId().equals(skypeNames)) {
                            usr = user;
                            break;
                        }
                    }
                }
            }
            if (usr != null) {
                BuddyStatus bdyStatus = usr.getBuddyStatus();
                if (!usr.isAuthorized()) {
                    usr.setAuthorized(true);
                }
                LOGGER.fine("BDY (" + usr.getDisplayName() + "):'" + bdyStatus + "' :'" + BuddyStatus.ADDED + "'");
                if (!usr.getBuddyStatus().equals(BuddyStatus.ADDED)) {
                    try {
                        Skype.getContactList().addFriend(usr, "The Skype Service on " + InetAddress.getLocalHost().getHostName() + " wants to notify you");
                    } catch (UnknownHostException ex) {
                        Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, null, ex);
                        throw new SkypeIMException(ex);
                    }
                    result = usr.getId();
                }
            }
        } catch (SkypeException ex) {
            throw new SkypeIMException(ex);
        }



        return result;
    }
}
