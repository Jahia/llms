// Breaks ACL inheritance on a node, in both workspaces, and grants nothing: after this, nobody but root
// and the system session reads it, visitors without an account included.
//
// Both workspaces on purpose. The module reads `live` as guest, so breaking it only in `default` would
// leave the published copy readable and the assertion would measure nothing.
//
// @jahia/cypress substitutes NODE_PATH.
import org.jahia.services.content.JCRCallback
import org.jahia.services.content.JCRSessionWrapper
import org.jahia.services.content.JCRTemplate

['default', 'live'].each { workspace ->
    JCRTemplate.instance.doExecuteWithSystemSession(null, workspace, null, { JCRSessionWrapper session ->
        def node = session.nodeExists('NODE_PATH') ? session.getNode('NODE_PATH') : null
        if (node == null) {
            return null
        }
        node.setAclInheritanceBreak(true)
        session.save()
        return null
    } as JCRCallback)
}
