/**
 * Copyright (C) 2009 eXo Platform SAS.
 * 
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 * 
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.exoplatform.portal.config;

import junit.framework.AssertionFailedError;

import org.exoplatform.container.PortalContainer;
import org.exoplatform.portal.application.PortletPreferences;
import org.exoplatform.portal.config.model.ApplicationState;
import org.exoplatform.portal.config.model.Container;
import org.exoplatform.portal.config.model.Page;
import org.exoplatform.portal.config.model.PageBody;
import org.exoplatform.portal.config.model.PageNavigation;
import org.exoplatform.portal.config.model.PortalConfig;
import org.exoplatform.portal.config.model.portlet.PortletApplication;
import org.exoplatform.portal.pom.config.POMSession;
import org.exoplatform.portal.pom.config.POMSessionManager;
import org.exoplatform.portal.pom.spi.portlet.Preferences;
import org.exoplatform.portal.pom.spi.portlet.PreferencesBuilder;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.User;
import org.exoplatform.services.organization.UserHandler;
import org.exoplatform.services.organization.jbidm.JBossIDMService;
import org.exoplatform.services.security.Authenticator;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.test.BasicTestCase;
import org.jboss.identity.idm.api.IdentitySession;
import org.jboss.identity.idm.common.exception.IdentityException;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author <a href="mailto:julien.viet@exoplatform.com">Julien Viet</a>
 * @version $Revision$
 */
public class TestUserPortalConfigService extends BasicTestCase
{

   /** . */
   private UserPortalConfigService userPortalConfigSer_;

   /** . */
   private OrganizationService orgService_;

   /** . */
   private DataStorage storage_;

   /** . */
   private JBossIDMService idmService;

   /** . */
   private POMSessionManager mgr;

   /** . */
   private Authenticator authenticator;

   /** . */
   private ListenerService listenerService;

   /** . */
   private LinkedList<Event> events;

   /** . */
   private boolean registered;

   public TestUserPortalConfigService(String name)
   {
      super(name);

      //
      registered = false;
   }

   @Override
   protected void setUp() throws Exception
   {

      //
      Listener listener = new Listener()
      {
         @Override
         public void onEvent(Event event) throws Exception
         {
            events.add(event);
         }
      };

      PortalContainer container = PortalContainer.getInstance();
      userPortalConfigSer_ =
         (UserPortalConfigService)container.getComponentInstanceOfType(UserPortalConfigService.class);
      orgService_ = (OrganizationService)container.getComponentInstanceOfType(OrganizationService.class);
      idmService = (JBossIDMService)container.getComponentInstanceOfType(JBossIDMService.class);
      mgr = (POMSessionManager)container.getComponentInstanceOfType(POMSessionManager.class);
      authenticator = (Authenticator)container.getComponentInstanceOfType(Authenticator.class);
      listenerService = (ListenerService)container.getComponentInstanceOfType(ListenerService.class);
      events = new LinkedList<Event>();
      storage_ = (DataStorage)container.getComponentInstanceOfType(DataStorage.class);

      // Register only once for all unit tests
      if (!registered)
      {
         // I'm using this due to crappy design of org.exoplatform.services.listener.ListenerService
         listenerService.addListener(UserPortalConfigService.CREATE_PAGE_EVENT, listener);
         listenerService.addListener(UserPortalConfigService.REMOVE_PAGE_EVENT, listener);
         listenerService.addListener(UserPortalConfigService.UPDATE_PAGE_EVENT, listener);
         listenerService.addListener(UserPortalConfigService.CREATE_NAVIGATION_EVENT, listener);
         listenerService.addListener(UserPortalConfigService.REMOVE_NAVIGATION_EVENT, listener);
         listenerService.addListener(UserPortalConfigService.UPDATE_NAVIGATION_EVENT, listener);
      }
   }

   private static Map<String, PageNavigation> toMap(UserPortalConfig cfg)
   {
      return toMap(cfg.getNavigations());
   }

   private static Map<String, PageNavigation> toMap(List<PageNavigation> navigations)
   {
      Map<String, PageNavigation> map = new HashMap<String, PageNavigation>();
      for (PageNavigation nav : navigations)
      {
         map.put(nav.getOwnerType() + "::" + nav.getOwnerId(), nav);
      }
      return map;
   }

   public void testRootGetUserPortalConfig()
   {
      new UnitTest()
      {
         public void execute() throws Exception
         {
            UserPortalConfig userPortalCfg = userPortalConfigSer_.getUserPortalConfig("classic", "root");
            assertNotNull(userPortalCfg);
            PortalConfig portalCfg = userPortalCfg.getPortalConfig();
            assertNotNull(portalCfg);
            assertEquals(PortalConfig.PORTAL_TYPE, portalCfg.getType());
            assertEquals("classic", portalCfg.getName());
            assertNotNull(userPortalCfg.getNavigations());
            Map<String, PageNavigation> navigations = toMap(userPortalCfg);
            assertEquals(5, navigations.size());
            assertTrue(navigations.containsKey("portal::classic"));
            assertTrue(navigations.containsKey("group::/platform/administrators"));
            assertTrue(navigations.containsKey("group::/organization/management/executive-board"));
            assertTrue(navigations.containsKey("group::/platform/users"));
            assertTrue(navigations.containsKey("user::root"));
         }
      }.execute("root");
   }

   public void testJohnGetUserPortalConfig()
   {
      new UnitTest()
      {
         public void execute() throws Exception
         {
            UserPortalConfig userPortalCfg = userPortalConfigSer_.getUserPortalConfig("classic", "john");
            assertNotNull(userPortalCfg);
            PortalConfig portalCfg = userPortalCfg.getPortalConfig();
            assertNotNull(portalCfg);
            assertEquals(PortalConfig.PORTAL_TYPE, portalCfg.getType());
            assertEquals("classic", portalCfg.getName());
            assertNotNull(userPortalCfg.getNavigations());
            Map<String, PageNavigation> navigations = toMap(userPortalCfg);
            assertEquals(5, navigations.size());
            assertTrue(navigations.containsKey("portal::classic"));
            assertTrue(navigations.containsKey("group::/platform/administrators"));
            assertTrue(navigations.containsKey("group::/organization/management/executive-board"));
            assertTrue(navigations.containsKey("group::/platform/users"));
            assertTrue(navigations.containsKey("user::john"));
         }
      }.execute("john");
   }

   public void testMaryGetUserPortalConfig()
   {
      new UnitTest()
      {
         public void execute() throws Exception
         {
            UserPortalConfig userPortalCfg = userPortalConfigSer_.getUserPortalConfig("classic", "mary");
            assertNotNull(userPortalCfg);
            PortalConfig portalCfg = userPortalCfg.getPortalConfig();
            assertNotNull(portalCfg);
            assertEquals(PortalConfig.PORTAL_TYPE, portalCfg.getType());
            assertEquals("classic", portalCfg.getName());
            assertNotNull(userPortalCfg.getNavigations());
            Map<String, PageNavigation> navigations = toMap(userPortalCfg);
            assertEquals(3, navigations.size());
            assertTrue(navigations.containsKey("portal::classic"));
            assertTrue(navigations.containsKey("group::/platform/users"));
            assertTrue(navigations.containsKey("user::mary"));
         }
      }.execute("mary");
   }

   public void testGuestGetUserPortalConfig()
   {
      new UnitTest()
      {
         public void execute() throws Exception
         {
            UserPortalConfig userPortalCfg = userPortalConfigSer_.getUserPortalConfig("classic", null);
            assertNotNull(userPortalCfg);
            PortalConfig portalCfg = userPortalCfg.getPortalConfig();
            assertNotNull(portalCfg);
            assertEquals(PortalConfig.PORTAL_TYPE, portalCfg.getType());
            assertEquals("classic", portalCfg.getName());
            assertNotNull(userPortalCfg.getNavigations());
            Map<String, PageNavigation> navigations = toMap(userPortalCfg);
            assertEquals("" + navigations, 1, navigations.size());
            assertTrue(navigations.containsKey("portal::classic"));
         }
      }.execute(null);
   }

   public void testNavigationOrder()
   {
      new UnitTest()
      {
         public void execute() throws Exception
         {
            UserPortalConfig userPortalCfg = userPortalConfigSer_.getUserPortalConfig("classic", "root");
            List<PageNavigation> navigations = userPortalCfg.getNavigations();
            assertEquals(5, navigations.size());
            assertEquals("classic", navigations.get(0).getOwnerId()); // 1
            assertEquals("/platform/administrators", navigations.get(1).getOwnerId()); // 2
            assertEquals("root", navigations.get(2).getOwnerId()); // 3
            assertEquals("/organization/management/executive-board", navigations.get(3).getOwnerId()); // 5
            assertEquals("/platform/users", navigations.get(4).getOwnerId()); // 8
         }
      }.execute("root");
   }

   public void testCreateUserPortalConfig()
   {
      new UnitTest()
      {
         public void execute() throws Exception
         {
            userPortalConfigSer_.createUserPortalConfig(PortalConfig.PORTAL_TYPE, "jazz", "test");
            UserPortalConfig userPortalCfg = userPortalConfigSer_.getUserPortalConfig("jazz", "root");
            assertNotNull(userPortalCfg);
            PortalConfig portalCfg = userPortalCfg.getPortalConfig();
            assertNotNull(portalCfg);
            assertEquals(PortalConfig.PORTAL_TYPE, portalCfg.getType());
            assertEquals("jazz", portalCfg.getName());
            assertNotNull(userPortalCfg.getNavigations());
            Map<String, PageNavigation> navigations = toMap(userPortalCfg);
            assertEquals(5, navigations.size());
            assertTrue(navigations.containsKey("portal::jazz"));
            assertTrue(navigations.containsKey("group::/platform/administrators"));
            assertTrue(navigations.containsKey("group::/organization/management/executive-board"));
            assertTrue(navigations.containsKey("group::/platform/users"));
            assertTrue(navigations.containsKey("user::root"));

            queryPage();
         }

         private void queryPage()
         {
            Query<Page> query = new Query<Page>("portal", null, null, null, Page.class);
            try
            {
               storage_.find(query);
            }
            catch (Exception ex)
            {
               assertTrue("Exception while querying pages with new portal", false);
            }
         }

      }.execute("root");
   }

   public void testRemoveUserPortalConfig()
   {
      new UnitTest()
      {
         public void execute() throws Exception
         {
            userPortalConfigSer_.createUserPortalConfig(PortalConfig.PORTAL_TYPE, "jazz", "test");
            UserPortalConfig userPortalCfg = userPortalConfigSer_.getUserPortalConfig("jazz", "root");
            assertNotNull(userPortalCfg);
            saveMOP();
            userPortalConfigSer_.removeUserPortalConfig("jazz");
            saveMOP();
            assertNull(userPortalConfigSer_.getUserPortalConfig("jazz", "root"));
         }
      }.execute("root");
   }

   public void testRootGetMakableNavigations()
   {
      new UnitTest()
      {
         public void execute() throws Exception
         {
            Set<String> navigations = new HashSet<String>(userPortalConfigSer_.getMakableNavigations("root"));
            Set<String> expectedNavigations =
               new HashSet<String>(Arrays.asList("/platform/users", "/organization/management/human-resources",
                  "/partners", "/customers", "/organization/communication", "/organization/management/executive-board",
                  "/organization/management", "/organization/operations", "/organization", "/platform",
                  "/organization/communication/marketing", "/platform/guests",
                  "/organization/communication/press-and-media", "/platform/administrators",
                  "/organization/operations/sales", "/organization/operations/finances"));
            assertEquals(expectedNavigations, navigations);
         }
      }.execute(null);
   }

   public void testJohnGetMakableNavigations()
   {
      new UnitTest()
      {
         public void execute() throws Exception
         {
            Set<String> navigations = new HashSet<String>(userPortalConfigSer_.getMakableNavigations("john"));
            Set<String> expectedNavigations = Collections.singleton("/organization/management/executive-board");
            assertEquals(expectedNavigations, navigations);
         }
      }.execute(null);
   }

   public void testMaryGetMakableNavigations()
   {
      new UnitTest()
      {
         public void execute() throws Exception
         {
            Set<String> navigations = new HashSet<String>(userPortalConfigSer_.getMakableNavigations("mary"));
            Set<String> expectedNavigations = Collections.emptySet();
            assertEquals(expectedNavigations, navigations);
         }
      }.execute(null);
   }

   public void testRootGetPage()
   {
      new UnitTest()
      {
         public void execute() throws Exception
         {
            assertEquals("group::/platform/administrators::newAccount", userPortalConfigSer_.getPage(
               "group::/platform/administrators::newAccount", null).getPageId());
            assertEquals("group::/organization/management/executive-board::newStaff", userPortalConfigSer_.getPage(
               "group::/organization/management/executive-board::newStaff", null).getPageId());
         }
      }.execute("root");
   }

   public void testJohnGetPage()
   {
      new UnitTest()
      {
         public void execute() throws Exception
         {
            assertEquals(null, userPortalConfigSer_.getPage("group::/platform/administrators::newAccount", null));
            assertEquals("group::/organization/management/executive-board::newStaff", userPortalConfigSer_.getPage(
               "group::/organization/management/executive-board::newStaff", null).getPageId());
         }
      }.execute("john");
   }

   public void testMaryGetPage()
   {
      new UnitTest()
      {
         public void execute() throws Exception
         {
            assertEquals(null, userPortalConfigSer_.getPage("group::/platform/administrators::newAccount", null));
            assertEquals(null, userPortalConfigSer_.getPage(
               "group::/organization/management/executive-board::newStaff", null));
         }
      }.execute("mary");
   }

   public void testAnonymousGetPage()
   {
      new UnitTest()
      {
         public void execute() throws Exception
         {
            assertEquals(null, userPortalConfigSer_.getPage("group::/platform/administrators::newAccount", null));
            assertEquals(null, userPortalConfigSer_.getPage(
               "group::/organization/management/executive-board::newStaff", null));
         }
      }.execute(null);
   }

   public void testRemovePage()
   {
      new UnitTest()
      {
         public void execute() throws Exception
         {
            Page page = new Page();
            page.setOwnerType("group");
            page.setOwnerId("/platform/administrators");
            page.setName("newAccount");
            assertTrue(events.isEmpty());
            userPortalConfigSer_.remove(page);
            assertEquals(1, events.size());
            Event event = events.removeFirst();
            assertEquals(UserPortalConfigService.REMOVE_PAGE_EVENT, event.getEventName());
            Page p = ((Page)event.getData());
            assertEquals("group", p.getOwnerType());
            assertEquals("/platform/administrators", p.getOwnerId());
            assertEquals("newAccount", p.getName());
            assertEquals(null, userPortalConfigSer_.getPage("group::/platform/administrators::newAccount"));
         }
      }.execute(null);
   }

   public void testCreatePage()
   {
      new UnitTest()
      {
         public void execute() throws Exception
         {
            Page page = new Page();
            page.setOwnerType("group");
            page.setOwnerId("/platform/administrators");
            page.setName("whatever");
            assertTrue(events.isEmpty());
            userPortalConfigSer_.create(page);
            assertEquals(1, events.size());
            Event event = events.removeFirst();
            assertEquals(UserPortalConfigService.CREATE_PAGE_EVENT, event.getEventName());
            Page p = ((Page)event.getData());
            assertEquals("group", p.getOwnerType());
            assertEquals("/platform/administrators", p.getOwnerId());
            assertEquals("whatever", p.getName());
            assertNotNull(userPortalConfigSer_.getPage("group::/platform/administrators::whatever"));
         }
      }.execute(null);
   }


   // Julien : see who added that and find out is test is relevant or not
/*
   public void testClonePage()
   {
      new UnitTest()
      {
         public void execute() throws Exception
         {
            Page page = new Page();
            page.setOwnerType("group");
            page.setOwnerId("/platform/administrators");
            page.setName("whatever");
            page.setTitle("testTitle");
            userPortalConfigSer_.create(page);
            
            String newName = "newPage";
            Page newPage = userPortalConfigSer_.renewPage(page.getPageId(), newName, page.getOwnerType(), page.getOwnerId());
            assertEquals(newName, newPage.getName());   
            assertEquals(page.getTitle(), newPage.getTitle());   
         }
      }.execute(null);
   }
*/

   
   public void testUpdatePage()
   {
      new UnitTest()
      {
         public void execute() throws Exception
         {
            Page page = new Page();
            page.setOwnerType("group");
            page.setOwnerId("/platform/administrators");
            page.setName("newAccount");
            page.setCreator("someone");
            assertTrue(events.isEmpty());
            userPortalConfigSer_.create(page);
            assertEquals(1, events.size());
            Event event = events.removeFirst();
            assertEquals(UserPortalConfigService.CREATE_PAGE_EVENT, event.getEventName());
            Page p = ((Page)event.getData());
            assertEquals("group", p.getOwnerType());
            assertEquals("/platform/administrators", p.getOwnerId());
            assertEquals("newAccount", p.getName());
            assertEquals("someone", p.getCreator());
            Page p2 = userPortalConfigSer_.getPage("group::/platform/administrators::newAccount");
            assertEquals("group", p2.getOwnerType());
            assertEquals("/platform/administrators", p2.getOwnerId());
            assertEquals("newAccount", p2.getName());
            assertEquals("someone", p2.getCreator());
         }
      }.execute(null);
   }

   public void testRemoveNavigation()
   {
      new UnitTest()
      {
         public void execute() throws Exception
         {
            PageNavigation navigation = new PageNavigation();
            navigation.setOwnerType("group");
            navigation.setOwnerId("/platform/administrators");
            assertTrue(events.isEmpty());
            userPortalConfigSer_.remove(navigation);
            assertEquals(1, events.size());
            Event event = events.removeFirst();
            assertEquals(UserPortalConfigService.REMOVE_NAVIGATION_EVENT, event.getEventName());
            PageNavigation n = ((PageNavigation)event.getData());
            assertEquals("group", n.getOwnerType());
            assertEquals("/platform/administrators", n.getOwnerId());
            assertEquals(null, userPortalConfigSer_.getPageNavigation("group", "/platform/administrators"));
         }
      }.execute(null);
   }

   public void testCreateNavigation()
   {
      new UnitTest()
      {
         public void execute() throws Exception
         {
            PageNavigation navigation = new PageNavigation();
            navigation.setOwnerType("group");
            navigation.setOwnerId("/platform/administrators");
            userPortalConfigSer_.remove(navigation);
            assertNotNull(events.removeLast());
            assertTrue(events.isEmpty());
            userPortalConfigSer_.create(navigation);
            assertEquals(1, events.size());
            Event event = events.removeFirst();
            assertEquals(UserPortalConfigService.CREATE_NAVIGATION_EVENT, event.getEventName());
            PageNavigation n = ((PageNavigation)event.getData());
            assertEquals("group", n.getOwnerType());
            assertEquals("/platform/administrators", n.getOwnerId());
            PageNavigation n2 = userPortalConfigSer_.getPageNavigation("group", "/platform/administrators");
            assertEquals("group", n2.getOwnerType());
            assertEquals("/platform/administrators", n2.getOwnerId());
         }
      }.execute(null);
   }

   public void testUpdateNavigation()
   {
      new UnitTest()
      {
         public void execute() throws Exception
         {
            PageNavigation navigation = new PageNavigation();
            navigation.setOwnerType("group");
            navigation.setOwnerId("/platform/administrators");
            navigation.setPriority(3);
            assertTrue(events.isEmpty());
            userPortalConfigSer_.update(navigation);
            assertEquals(1, events.size());
            Event event = events.removeFirst();
            assertEquals(UserPortalConfigService.UPDATE_NAVIGATION_EVENT, event.getEventName());
            PageNavigation n = ((PageNavigation)event.getData());
            assertEquals("group", n.getOwnerType());
            assertEquals("/platform/administrators", n.getOwnerId());
            assertEquals(3, n.getPriority());
            PageNavigation n2 = userPortalConfigSer_.getPageNavigation("group", "/platform/administrators");
            assertEquals("group", n2.getOwnerType());
            assertEquals("/platform/administrators", n2.getOwnerId());
            assertEquals(3, n2.getPriority());
         }
      }.execute(null);
   }

   public void testRenewPage()
   {
      new UnitTest()
      {
         public void execute() throws Exception
         {
            Page clone = userPortalConfigSer_.renewPage("portal::test::test4", "test5", "portal", "test");
            assertNotNull(clone);
            assertEquals("portal", clone.getOwnerType());
            assertEquals("test", clone.getOwnerId());
            assertEquals("test5", clone.getName());

            //
            PortletApplication app = (PortletApplication)clone.getChildren().get(0);
            Preferences prefs2 = storage_.load(app.getState());
            assertEquals(new PreferencesBuilder().add("template",
               "par:/groovy/groovy/webui/component/UIBannerPortlet.gtmpl").build(), prefs2);

            // Update prefs of original page
            PortletPreferences prefs = new PortletPreferences();
            prefs.setWindowId("portal#test:/web/BannerPortlet/banner");
            storage_.save(prefs);

            //
            prefs2 = storage_.load(app.getState());
            assertEquals(new PreferencesBuilder().add("template",
               "par:/groovy/groovy/webui/component/UIBannerPortlet.gtmpl").build(), prefs2);
         }
      }.execute(null);
   }

   public void testCreateFromTemplate()
   {
      new UnitTest()
      {
         public void execute() throws Exception
         {
            Page clone = userPortalConfigSer_.createPageTemplate("dashboard", "portal", "test");
            assertNotNull(clone);
            assertEquals("portal", clone.getOwnerType());
            assertEquals("test", clone.getOwnerId());

            //
            assertEquals(1, clone.getChildren().size());

            //
            PortletApplication app = (PortletApplication)clone.getChildren().get(0);
            assertEquals("Dashboard", app.getTitle());
            assertNotNull(app.getState());
            assertEquals("dashboard", app.getRef().getApplicationName());
            assertEquals("DashboardPortlet", app.getRef().getPortletName());
            //        assertEquals("portal", app.getInstanceState().getOwnerType());
            //        assertEquals("test", app.getInstanceState().getOwnerId());
            Preferences prefs2 = storage_.load(app.getState());
            assertNull(prefs2);
         }
      }.execute(null);
   }

   public void testOverwriteUserLayout()
   {
      new UnitTest()
      {
         public void execute() throws Exception
         {
            PortalConfig cfg = storage_.getPortalConfig(PortalConfig.USER_TYPE, "overwritelayout");
            assertNotNull(cfg);
            Container container = cfg.getPortalLayout();
            assertNotNull(container);
            assertEquals(2, container.getChildren().size());
            assertTrue(container.getChildren().get(0) instanceof PageBody);
            assertTrue(container.getChildren().get(1) instanceof PortletApplication);
            PortletApplication pa = (PortletApplication)container.getChildren().get(1);
            ApplicationState state = pa.getState();
            assertEquals("overwrite_application_ref", pa.getRef().getApplicationName());
            assertEquals("overwrite_portlet_ref", pa.getRef().getPortletName());
         }
      }.execute(null);
   }

   public void testUserTemplate()
   {
      new UnitTest()
      {
         public void execute() throws Exception
         {
            assertNull(storage_.getPortalConfig(PortalConfig.USER_TYPE, "user"));
            assertNull(storage_.getPortalConfig(PortalConfig.USER_TYPE, "julien"));

            //
            UserHandler userHandler = orgService_.getUserHandler();
            User user = userHandler.createUserInstance("julien");
            user.setPassword("default");
            user.setFirstName("default");
            user.setLastName("default");
            user.setEmail("exo@exoportal.org");
            userHandler.createUser(user, true);

            //
            PortalConfig cfg = storage_.getPortalConfig(PortalConfig.USER_TYPE, "julien");
            assertNotNull(cfg);
            Container container = cfg.getPortalLayout();
            assertNotNull(container);
            assertEquals(2, container.getChildren().size());
            assertTrue(container.getChildren().get(0) instanceof PageBody);
            assertTrue(container.getChildren().get(1) instanceof PortletApplication);
            PortletApplication pa = (PortletApplication)container.getChildren().get(1);
            ApplicationState state = pa.getState();
            assertEquals("foo", pa.getRef().getApplicationName());
            assertEquals("bar", pa.getRef().getPortletName());

         }
      }.execute(null);
   }

   private abstract class UnitTest
   {

      /** . */
      private POMSession mopSession;

      protected final void execute(String userId)
      {
         Throwable failure = null;

         //
         ConversationState conversationState = null;
         if (userId != null)
         {
            try
            {
               conversationState = new ConversationState(authenticator.createIdentity(userId));
            }
            catch (Exception e)
            {
               failure = e;
            }
         }

         //
         if (failure == null)
         {
            IdentitySession session = null;
            try
            {
               session = idmService.getIdentitySession();
               session.beginTransaction();
            }
            catch (Exception e)
            {
               failure = e;
            }

            //
            mopSession = mgr.openSession();
            if (failure == null)
            {
               ConversationState.setCurrent(conversationState);
               try
               {

                  //
                  execute();
               }
               catch (Exception e)
               {
                  failure = e;
               }
               finally
               {
                  ConversationState.setCurrent(null);
                  mopSession = null;
                  mgr.closeSession();
                  try
                  {
                     session.close();
                  }
                  catch (IdentityException e)
                  {
                     if (failure == null)
                     {
                        failure = e;
                     }
                  }
               }
            }
         }

         // Report error as a junit assertion failure
         if (failure != null)
         {
            AssertionFailedError err = new AssertionFailedError();
            err.initCause(failure);
            throw err;
         }
      }

      protected final void saveMOP()
      {
         mopSession.save();
      }

      protected abstract void execute() throws Exception;

   }
}
