package se.webinventions.mongomultitenant
import org.springframework.context.ApplicationContext;
import org.codehaus.groovy.grails.web.context.ServletContextHolder;
import org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes;

public class SpringUtil {

    public static ApplicationContext getCtx() {
        return getApplicationContext();
    }

    public static ApplicationContext getApplicationContext() {
        return (ApplicationContext) ServletContextHolder.getServletContext().getAttribute(GrailsApplicationAttributes.APPLICATION_CONTEXT);
    }

    @SuppressWarnings("unchecked")
    public static <T> T getBean(String beanName) {
        return (T) getApplicationContext().getBean(beanName);
    }

}