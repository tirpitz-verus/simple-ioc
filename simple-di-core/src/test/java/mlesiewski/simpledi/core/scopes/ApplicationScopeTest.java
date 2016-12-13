package mlesiewski.simpledi.core.scopes;

import mlesiewski.simpledi.core.SimpleDiException;
import mlesiewski.simpledi.core.testutils.NewObjectProvider;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class ApplicationScopeTest {

    private ApplicationScope applicationScope;

    @Test(expectedExceptions = SimpleDiException.class)
    public void throwsExceptionOnEnd() throws Exception {
        // when
        applicationScope.end();
        // then - exception
    }

    @Test
    public void startCallsRegisteredProviders() throws Exception {
        // given
        NewObjectProvider provider = new NewObjectProvider();

        // when
        applicationScope.register(provider, "name");
        // then
        assertFalse(provider.provideCalled);
        assertFalse(provider.softDependenciesSet);

        // when
        applicationScope.start();
        // then
        assertTrue(provider.provideCalled);
        assertTrue(provider.softDependenciesSet);
    }

    @Test
    public void getBeanUsesEagerBeanCache() throws Exception {
        // given
        String name = "name";
        applicationScope.eagerBeanCache.put(name, name);
        // when
        String bean = applicationScope.getBean(name);
        // then
        assertThat(bean, is(name));
    }

    @BeforeMethod
    public void setUp() throws Exception {
        applicationScope = new ApplicationScope();
    }
}