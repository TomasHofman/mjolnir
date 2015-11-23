package org.jboss.mjolnir.client.application.admin.gitHubMembers;

import java.util.Collection;
import java.util.List;

import javax.inject.Inject;

import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.Presenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.NameToken;
import com.gwtplatform.mvp.client.annotations.ProxyStandard;
import com.gwtplatform.mvp.client.annotations.UseGatekeeper;
import com.gwtplatform.mvp.client.proxy.ProxyPlace;
import org.jboss.mjolnir.client.NameTokens;
import org.jboss.mjolnir.client.application.ApplicationPresenter;
import org.jboss.mjolnir.client.application.security.IsAdminGatekeeper;
import org.jboss.mjolnir.client.service.AdministrationService;
import org.jboss.mjolnir.client.service.AdministrationServiceAsync;
import org.jboss.mjolnir.client.service.DefaultCallback;
import org.jboss.mjolnir.shared.domain.Subscription;
import org.jboss.mjolnir.shared.domain.SubscriptionSummary;

/**
 * Shows members of configured GitHub organizations.
 *
 * Allows to:
 * * whitelist
 * * unsubscribe from organization
 *
 * @author Tomas Hofman (thofman@redhat.com)
 */
public class GitHubMembersPresenter extends Presenter<GitHubMembersPresenter.MyView, GitHubMembersPresenter.MyProxy>
        implements GitHubMembersHandlers {

    public interface MyView extends View, HasUiHandlers<GitHubMembersHandlers> {
        void setData(List<SubscriptionSummary> items);
        List<Subscription> getCurrentSubscriptionList();
        void refresh();
    }

    @ProxyStandard
    @NameToken(NameTokens.GITHUB_MEMBERS)
    @UseGatekeeper(IsAdminGatekeeper.class)
    public interface MyProxy extends ProxyPlace<GitHubMembersPresenter> {}

    private AdministrationServiceAsync administrationService = AdministrationService.Util.getInstance();

    @Inject
    public GitHubMembersPresenter(EventBus eventBus, MyView view, MyProxy proxy) {
        super(eventBus, view, proxy, ApplicationPresenter.SLOT_CONTENT);
        getView().setUiHandlers(this);
    }

    @Override
    protected void onReveal() {
        administrationService.getOrganizationMembers(new DefaultCallback<List<SubscriptionSummary>>() {
            @Override
            public void onSuccess(List<SubscriptionSummary> result) {
                getView().setData(result);
            }
        });
    }

    @Override
    public void unsubscribeUsers(final List<Subscription> selectedItems) {
        administrationService.unsubscribe(selectedItems, new DefaultCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                // remove selected items from currently displayed list
                getView().getCurrentSubscriptionList().removeAll(selectedItems);
                getView().refresh();
            }
        });
    }

    @Override
    public void whitelist(List<Subscription> selectedItems, boolean whitelist) {
        administrationService.whitelist(selectedItems, whitelist, new DefaultCallback<Collection<Subscription>>() {
            @Override
            public void onSuccess(Collection<Subscription> result) {
                // update items in currently displayed list
                List<Subscription> currentSubscriptions = getView().getCurrentSubscriptionList();
                for (Subscription subscription: result) {
                    int idx = currentSubscriptions.indexOf(subscription);
                    if (idx > -1) {
                        Subscription originalSubscription = currentSubscriptions.get(idx);
                        originalSubscription.setKerberosUser(subscription.getKerberosUser());
                    }
                }
                getView().refresh();
            }
        });
    }

}