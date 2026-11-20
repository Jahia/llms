package org.jahia.modules.llms.service;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.Designate;

import java.util.HashMap;
import java.util.Map;

/**
 * One site configuration ({@code org.jahia.modules.llms.site-<name>.cfg}): there is one of these for
 * each such file, and none without a file. {@link LlmsService} reads them when it generates for a site.
 */
@Component(service = SiteOverride.class, configurationPid = "org.jahia.modules.llms.site",
        configurationPolicy = ConfigurationPolicy.REQUIRE)
@Designate(ocd = LlmsSiteConfig.class, factory = true)
public class SiteOverride {

    private static final String FILE_PREFIX = "org.jahia.modules.llms.site-";
    private volatile Map<String, Object> properties = Map.of();

    /**
     * @param configured the properties of the configuration
     */
    @Activate
    @Modified
    public void activate(Map<String, Object> configured) {
        this.properties = Map.copyOf(new HashMap<>(configured));
    }

    /**
     * @return the key of the site this configuration is for, or an empty text when it names none
     */
    public String siteKey() {
        Object key = properties.get("siteKey");
        return key == null ? "" : String.valueOf(key).trim();
    }

    /**
     * The name of this configuration: what follows {@code org.jahia.modules.llms.site-} in the name of its
     * file. Jahia's configuration service finds the configuration again by it.
     *
     * @return the name, or null when the configuration was not made from a named file
     */
    public String identifier() {
        Object pid = properties.get("service.pid");
        String text = pid == null ? "" : String.valueOf(pid);
        int at = text.indexOf('~');
        if (at >= 0 && at < text.length() - 1) {
            return text.substring(at + 1);
        }
        // An older naming of the configurations made from a file: the file is then named by a property.
        Object file = properties.get("felix.fileinstall.filename");
        String name = file == null ? "" : String.valueOf(file);
        name = name.substring(name.lastIndexOf('/') + 1);
        int dash = name.indexOf(FILE_PREFIX);
        int dot = name.lastIndexOf('.');
        return dash == 0 && dot > FILE_PREFIX.length() ? name.substring(FILE_PREFIX.length(), dot) : null;
    }

    /**
     * @return the properties of the configuration
     */
    public Map<String, Object> properties() {
        return properties;
    }
}
