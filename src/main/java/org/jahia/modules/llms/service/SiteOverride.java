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
     * @return the properties of the configuration
     */
    public Map<String, Object> properties() {
        return properties;
    }
}
