package org.jboss.set.mjolnir.client.application.error;


import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.Presenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.NameToken;
import com.gwtplatform.mvp.client.annotations.NoGatekeeper;
import com.gwtplatform.mvp.client.annotations.ProxyStandard;
import com.gwtplatform.mvp.client.proxy.ProxyPlace;
import org.jboss.set.mjolnir.client.NameTokens;

/**
 * @author Tomas Hofman (thofman@redhat.com)
 */
public class ErrorPresenter extends Presenter<ErrorPresenter.MyView, ErrorPresenter.MyProxy> {

    public interface MyView extends View {}

    @ProxyStandard
    @NameToken(NameTokens.ERROR)
    @NoGatekeeper
    public interface MyProxy extends ProxyPlace<ErrorPresenter> {}

    @Inject
    public ErrorPresenter(EventBus eventBus, MyView view, MyProxy proxy) {
        super(eventBus, view, proxy, RevealType.Root);
    }

}
