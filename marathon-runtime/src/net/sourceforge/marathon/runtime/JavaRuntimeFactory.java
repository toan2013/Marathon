/*******************************************************************************
 *  
 *  Copyright (C) 2010 Jalian Systems Private Ltd.
 *  Copyright (C) 2010 Contributors to Marathon OSS Project
 * 
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public
 *  License as published by the Free Software Foundation; either
 *  version 2 of the License, or (at your option) any later version.
 * 
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Library General Public License for more details.
 * 
 *  You should have received a copy of the GNU Library General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 * 
 *  Project website: http://www.marathontesting.com
 *  Help: Marathon help forum @ http://groups.google.com/group/marathon-testing
 * 
 *******************************************************************************/
package net.sourceforge.marathon.runtime;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sourceforge.marathon.Constants;
import net.sourceforge.marathon.Constants.MarathonMode;
import net.sourceforge.marathon.api.IConsole;
import net.sourceforge.marathon.api.IMarathonRuntime;
import net.sourceforge.marathon.api.IPlaybackListener;
import net.sourceforge.marathon.api.IRecorder;
import net.sourceforge.marathon.api.IRuntimeFactory;
import net.sourceforge.marathon.api.MarathonException;
import net.sourceforge.marathon.util.Path;
import net.sourceforge.rmilite.Client;

/**
 * This start the client server run time. In this case, it's implemented in
 * java. JavaRuntimeLauncher is the server JavaRuntmieLeash is the client
 */
public class JavaRuntimeFactory implements IRuntimeFactory {
    private Process process;
    private JavaRuntimeProfile profile;

    public synchronized IMarathonRuntime createRuntime(MarathonMode mode, String script, IConsole console) {
        profile = createProfile(mode, script);
        Client client = new Client("localhost", profile.getPort());
        client.exportInterface(IConsole.class);
        client.exportInterface(IRecorder.class);
        client.exportInterface(IPlaybackListener.class);
        try {
            this.process = launchVM(profile);
        } catch (Throwable t) {
            if (process != null)
                process.destroy();
            t.printStackTrace();
            throw new MarathonException("error creating Java Runtime: " + t.getMessage(), t);
        }
        return new JavaRuntimeLeash(client, process, console);
    }

    protected JavaRuntimeProfile createProfile(MarathonMode mode, String script) {
        return new JavaRuntimeProfile(mode, script);
    }

    public JavaRuntimeProfile getProfile() {
        return profile;
    }
    
    protected Process launchVM(JavaRuntimeProfile jprofile) throws IOException {
        String command = createCommand(jprofile);
        Logger.getLogger(JavaRuntimeFactory.class.getName()).log(Level.INFO, "launching: " + command);
        String[] cmdElements = getCommandArray(command);
        String dirName = jprofile.getWorkingDirectory();
        if (dirName.equals(""))
            dirName = ".";
        File workingDir = new File(dirName);
        Logger.getLogger(JavaRuntimeFactory.class.getName()).log(Level.INFO, "Classpath: " + jprofile.getClasspath());
        Path extendedClasspath = new Path(jprofile.getClasspath());
        Process process = Runtime.getRuntime().exec(cmdElements, getExtendedEnviron(extendedClasspath), workingDir);
        return process;
    }

    private String[] getExtendedEnviron(Path extendedClasspath) {
        Map<String, String> env = new HashMap<String, String>(System.getenv());
        env.put("CLASSPATH", extendedClasspath.toString());
        Set<String> keySet = env.keySet();
        String[] r = new String[keySet.size()];
        int i = 0;
        for (String string : keySet) {
            r[i++] = string + "=" + env.get(string);
        }
        return r;
    }

    private String[] getCommandArray(String command) {
        command = command.replaceAll("  ", " ");
        String[] arguments = command.split(" (?=([^\"]*\"[^\"]*\")*(?![^\"]*\"))");
        for (int i = 0; i < arguments.length; i++) {
            arguments[i] = escape(arguments[i]);
        }
        return arguments;
    }

    private String escape(String string) {
        if (string.startsWith("\""))
            string = string.substring(1);
        if (string.endsWith("\""))
            string = string.substring(0, string.length() - 1);
        return string;
    }

    private String createCommand(JavaRuntimeProfile profile) {
        MessageFormat launch_command = new MessageFormat("{0} {1} " + Constants.LAUNCHER_MAIN_CLASS + " {2,number,#} {3} {4}");
        return launch_command.format(new Object[] { profile.getVMCommand(), profile.getVMArgs(), Integer.valueOf(profile.getPort()), profile.getMainClass(),
                profile.getAppArgs() });
    }
}
