package mlesiewski.simpleioc;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;

public class DefaultScopeImplTest {

    static final Object BEAN = new Object();
    DefaultScopeImpl scope;
    boolean scopeEnded = false;
    int timesBeanWasProvided = 0;

    @Test
    public void doesNotHaveAnUnregisteredBean() throws Exception {
        // given
        scope.start();
        // when
        boolean hasBean = scope.hasBean("not registered");
        // then
        assertThat(hasBean, is(false));
    }

    @Test
    public void hasARegisteredBeanWhenStated() throws Exception {
        // given
        String name = "registered";
        scope.register(testBeanProvider(), name);
        scope.start();
        // when
        boolean hasBean = scope.hasBean(name);
        // then
        assertThat(hasBean, is(true));
    }

    @Test(expectedExceptions = SimpleInjectionCase.class)
    public void throwsExceptionIfGettingNotRegisteredName() throws Exception {
        // given
        String name = "not registered";
        // when
        scope.hasBean(name);
        // then - exception
    }

    @Test(expectedExceptions = SimpleInjectionCase.class)
    public void throwsExceptionIfGettingBeanAndNotStarted() throws Exception {
        // given
        String name = "registered";
        scope.register(testBeanProvider(), name);
        scope.end();
        // when
        scope.hasBean(name);
        // then - exception
    }

    @Test
    public void returnsRegisteredBeanWhenStated() throws Exception {
        // given
        String name = "registered";
        scope.register(testBeanProvider(), name);
        scope.start();
        // when
        Object bean = scope.getBean(name);
        // then
        assertThat(bean, is(BEAN));
    }

    @Test
    public void doesNotHaveARegisteredBeanWhenNotStated() throws Exception {
        // given
        String name = "registered";
        scope.register(testBeanProvider(), name);
        scope.end();
        // when
        boolean hasBean = scope.hasBean(name);
        // then
        assertThat(hasBean, is(false));
    }

    @Test
    public void notifiesAllProvidersAboutScopeEnding() throws Exception {
        // when
        scope.end();
        // then
        assertThat(scopeEnded, is(true));
    }

    @Test
    public void providerIsNotCalledIfBeanIsInCache(){
        // given
        String name = "bean";
        scope.beanCache.put(name, BEAN);
        scope.register(testBeanProvider(), name);
        scope.start();
        // when
        scope.getBean(name);
        // than
        assertThat(timesBeanWasProvided, is(0));
    }

    @Test
    public void beanCachingDoesNotCauseMemoryLeaks() {
        // given
        String name = "bean";
        Object bean = new Object();
        scope.beanCache.put(name, bean);
        // when
        bean = null;
        System.gc();
        bean = scope.beanCache.get(name);
        // then
        assertThat(bean, is(not(nullValue())));
    }

    private BeanProvider<Object> testBeanProvider() {
        return new BeanProvider<Object>() {
            @Override
            public Object provide() {
                return BEAN;
            }

            @Override
            public void scopeEnded() {
                scopeEnded = true;
            }
        };
    }

    @BeforeMethod
    public void setUp() throws Exception {
        scope = new DefaultScopeImpl("default");
        scopeEnded = false;
        timesBeanWasProvided = 0;
    }
}