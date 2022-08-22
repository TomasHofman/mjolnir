package org.jboss.set.mjolnir.server.bean;

import org.jboss.logging.Logger;
import org.jboss.set.mjolnir.client.exception.ApplicationException;
import org.jboss.set.mjolnir.server.ldap.LdapClient;
import org.jboss.set.mjolnir.server.util.KerberosUtils;

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.SearchResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@inheritDoc}
 *
 * @author Tomas Hofman (thofman@redhat.com)
 */
@Stateless
public class LdapRepositoryBean implements LdapRepository {

    private static final String CONTEXT_NAME = "ou=users,dc=redhat,dc=com";
    private static final int GROUPING_FACTOR = 50; // query for so many users at a time
    private static final String LDAP_SEARCH_ERROR = "LDAP search error: ";
    private static final Logger logger = Logger.getLogger(LdapRepositoryBean.class);

    @EJB
    ApplicationParameters applicationParameters;

    LdapClient ldapClient;

    @PostConstruct
    public void init() {
        // fetch ldap url
        final String ldapUrl = applicationParameters.getMandatoryParameter(ApplicationParameters.LDAP_URL_KEY);

        try {
            ldapClient = new LdapClient(ldapUrl);
        } catch (NamingException e) {
            throw new ApplicationException("Couldn't create LDAP client instance", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean checkUserExists(String uid) {
        try {
            String normalizedUid = KerberosUtils.normalizeUsername(uid);
            final NamingEnumeration<SearchResult> results =
                    ldapClient.search(CONTEXT_NAME, String.format("(|(uid=%s)(rhatPriorUid=%s))", normalizedUid, normalizedUid));
            final boolean found = results.hasMore();
            results.close();
            return found;
        } catch (NamingException e) {
            logger.error(LDAP_SEARCH_ERROR, e);
            throw new ApplicationException(LDAP_SEARCH_ERROR + e.getMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * Note: This implementation tries to minimize number of LDAP requests by searching for set of users in every query.
     * Exact number of users every query is searching for is determined by static property GROUPING_FACTOR.
     */
    @Override
    public Map<String, Boolean> checkUsersExists(Set<String> users) {
        logger.debugf("calling checkUsersExists for %d users", users.size());
        final Map<String, Boolean> result = new HashMap<>();
        final Iterator<String> iterator = users.iterator();
        final List<String> tempUserList = new ArrayList<>(GROUPING_FACTOR);
        while (iterator.hasNext()) {
            tempUserList.add(iterator.next());
            if (tempUserList.size() >= GROUPING_FACTOR || !iterator.hasNext()) {
                final Map<String, Boolean> tempResultMap = checkUsersSubsetExists(tempUserList);
                result.putAll(tempResultMap);
                tempUserList.clear();
            }
        }
        return result;
    }

    @Override
    public LdapUserRecord findUserRecord(String uid) {
        try {
            final NamingEnumeration<SearchResult> results =
                    ldapClient.search(CONTEXT_NAME, "(|(uid=" + uid + ")(rhatPriorUid=" + uid + "))");
            if (results.hasMore()) {
                SearchResult searchResult = results.next();
                String currentUid = (String) searchResult.getAttributes().get("uid").get();

                Attribute employeeNumberAttribute = searchResult.getAttributes().get("employeeNumber");
                String employeeNumberString = employeeNumberAttribute == null ? null : (String) employeeNumberAttribute.get();
                Integer employeeNumber = null;
                try {
                    employeeNumber = Integer.parseInt(employeeNumberString);
                } catch (NumberFormatException e) {
                    logger.errorf(e, "Can't parse employee number '%s'", employeeNumberString);
                }

                // add user's prior UIDs to the list
                ArrayList<String> priorUids = new ArrayList<>();
                Attribute priorUidAttr = searchResult.getAttributes().get("rhatPriorUid");
                if (priorUidAttr != null) {
                    NamingEnumeration<?> priorUidsEnum = priorUidAttr.getAll();
                    while (priorUidsEnum.hasMore()) {
                        String priorUid = (String) priorUidsEnum.next();
                        priorUids.add(priorUid);
                    }
                }

                return new LdapUserRecord(employeeNumber, currentUid, priorUids);
            } else {
                return null;
            }
        } catch (NamingException e) {
            throw new ApplicationException(LDAP_SEARCH_ERROR + e.getMessage(), e);
        }
    }

    private Map<String, Boolean> checkUsersSubsetExists(List<String> users) {
        try {
            logger.debugf("Querying LDAP for %d users", users.size());

            // build a query
            final StringBuilder query = new StringBuilder("(|");
            for (String uid: users) {
                query.append(String.format("(|(uid=%s)(rhatPriorUid=%s))", uid, uid));
            }
            query.append(")");

            final NamingEnumeration<SearchResult> searchResults =
                    ldapClient.search(CONTEXT_NAME, query.toString());

            // fill the result map with found users
            final Map<String, Boolean> result = new HashMap<>();
            while (searchResults.hasMore()) {
                final SearchResult record = searchResults.next();

                // add user's UID to the map of existing users
                String uid = (String) record.getAttributes().get("uid").get();
                result.put(uid, true);

                // add user's prior UIDs to the map of existing users
                Attribute priorUidAttr = record.getAttributes().get("rhatPriorUid");
                if (priorUidAttr != null) {
                    NamingEnumeration<?> priorUids = priorUidAttr.getAll();
                    while (priorUids.hasMore()) {
                        String priorUid = (String) priorUids.next();
                        result.put(priorUid, true);
                    }
                }
            }
            searchResults.close();

            // fill the result map with users that weren't found
            for (String uid: users) {
                if (!result.containsKey(uid)) {
                    result.put(uid, false);
                }
            }

            return result;
        } catch (NamingException e) {
            logger.error(LDAP_SEARCH_ERROR, e);
            throw new ApplicationException(LDAP_SEARCH_ERROR + e.getMessage(), e);
        }
    }

    public static class LdapUserRecord {
        private Integer employeeNumber;
        private String uid;
        private List<String> priorUids;

        public LdapUserRecord(Integer employeeNumber, String uid, List<String> priorUids) {
            this.employeeNumber = employeeNumber;
            this.uid = uid;
            this.priorUids = priorUids;
        }

        public Integer getEmployeeNumber() {
            return employeeNumber;
        }

        public String getUid() {
            return uid;
        }

        public List<String> getPriorUids() {
            return priorUids;
        }

        public List<String> getAllUids() {
            ArrayList<String> uids = new ArrayList<>(priorUids);
            uids.add(uid);
            return uids;
        }
    }
}
