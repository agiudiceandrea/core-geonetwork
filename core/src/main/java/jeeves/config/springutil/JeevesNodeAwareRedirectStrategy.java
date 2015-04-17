package jeeves.config.springutil;

import org.fao.geonet.NodeInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;

import java.io.IOException;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static jeeves.config.springutil.JeevesDelegatingFilterProxy.getApplicationContextFromServletContext;
import static jeeves.config.springutil.JeevesDelegatingFilterProxy.getServletContext;

/**
 * Created by Jesse on 2/17/14.
 */
public class JeevesNodeAwareRedirectStrategy extends DefaultRedirectStrategy implements RedirectStrategy {
    @Autowired
    ServletContext context;

    @Override
    public void sendRedirect(HttpServletRequest request, HttpServletResponse response, String url) throws IOException {
        final ConfigurableApplicationContext applicationContext = getApplicationContextFromServletContext(getServletContext(context));

        NodeInfo nodeInfo = applicationContext.getBean(NodeInfo.class);

        String newUrl = url.replace("@@nodeId@@", nodeInfo.getId());

        super.sendRedirect(request, response, newUrl);
    }
}
