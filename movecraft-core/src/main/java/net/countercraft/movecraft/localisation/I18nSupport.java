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

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public final class I18nSupport {
    private final ResourceBundle bundle;

    public I18nSupport(ResourceBundle bundle) {
        this.bundle = bundle;
    }

    public static I18nSupport read(Plugin plugin, Locale locale) {
        return new I18nSupport(ResourceBundle.getBundle("localisation.movecraftlang", locale));
    }

    public String get(String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException ignored) {
            return key;
        }
    }

    public String format(String fmt, Object... args) {
        return String.format(get(fmt), args);
    }
}
