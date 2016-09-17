/*
 * This file is part of Movecraft.
 *
 *     Movecraft is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Movecraft is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Movecraft.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.countercraft.movecraft.localisation;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;

public final class I18nSupport {
    private Properties languageFile;

    public void init(Plugin plugin, String locale) {
        languageFile = new Properties();

        File localisationDirectory = new File(plugin.getDataFolder().getAbsolutePath() + "/localisation");

        if (!localisationDirectory.exists()) {
            localisationDirectory.mkdirs();
        }

        InputStream is = null;
        try {
            is = new FileInputStream(
                    localisationDirectory.getAbsolutePath() + "/movecraftlang" + "_" + locale + ".properties");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        if (is == null) {
            plugin.getLogger().log(Level.SEVERE, "Critical Error in Localisation System");
            plugin.getServer().shutdown();
            return;
        }

        try {
            languageFile.load(is);
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getInternationalisedString(String key) {
        String ret = languageFile.getProperty(key);
        if (ret != null) {
            return ret;
        } else {
            return key;
        }
    }
}
