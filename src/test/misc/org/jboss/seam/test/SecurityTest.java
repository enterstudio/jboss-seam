package org.jboss.seam.test;

import java.io.Serializable;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Id;
import javax.servlet.ServletContext;

import org.hibernate.ejb.Ejb3Configuration;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.contexts.ContextAdaptor;
import org.jboss.seam.contexts.Contexts;
import org.jboss.seam.contexts.Lifecycle;
import org.jboss.seam.mock.MockAclObjectIdentity;
import org.jboss.seam.mock.MockAclPermission;
import org.jboss.seam.mock.MockHttpSession;
import org.jboss.seam.mock.MockSecureEntity;
import org.jboss.seam.mock.MockServletContext;
import org.jboss.seam.security.UsernamePasswordToken;
import org.jboss.seam.security.acl.AclProvider.RecipientType;
import org.jboss.seam.security.acl.JPAIdentityGenerator;
import org.testng.annotations.Test;
import org.jboss.seam.security.SeamSecurityManager;
import org.jboss.seam.Component;
import org.jboss.seam.security.acl.PersistentAclProvider;
import org.hibernate.ejb.EventListenerConfigurator;
import org.jboss.seam.mock.MockExternalContext;
import org.jboss.seam.core.Manager;
import org.jboss.seam.init.Initialization;

public class SecurityTest
{
  @Name("mock")
  class MockSecureEntityMethodId {
    private Integer id;
    public MockSecureEntityMethodId(Integer id) { this.id = id; }
    @Id public Integer getId() { return id; }
  }

  @Name("mock")
  class MockSecureEntityFieldId {
    @Id private Integer id;
    public MockSecureEntityFieldId(Integer id) { this.id = id; }
  }

  class MockCompositeId implements Serializable {
    private int fieldA;
    private String fieldB;
    @Override
    public String toString() {
      return String.format("%s,%s", fieldA, fieldB);
    }
    public MockCompositeId(int fieldA, String fieldB) {
      this.fieldA = fieldA;
      this.fieldB = fieldB;
    }
  }

  @Name("mock")
  class MockSecureEntityCompositeId {
    @Id private MockCompositeId id;
    public MockSecureEntityCompositeId(MockCompositeId id) { this.id = id; }
  }

  @Test
  public void testJPAIdentityGenerator()
  {
    JPAIdentityGenerator gen = new JPAIdentityGenerator();
    assert("mock:1234".equals(gen.generateIdentity(new MockSecureEntityMethodId(1234))));
    assert("mock:1234".equals(gen.generateIdentity(new MockSecureEntityFieldId(1234))));
    assert(null == gen.generateIdentity(new MockSecureEntityMethodId(null)));
    assert("mock:1234,abc".equals(gen.generateIdentity(new MockSecureEntityCompositeId(
      new MockCompositeId(1234, "abc")))));
  }

  @Test
  public void testPersistentAcls()
  {
    Ejb3Configuration ac = new Ejb3Configuration();

    ac.setProperty("hibernate.connection.driver_class", "org.hsqldb.jdbcDriver");
    ac.setProperty("hibernate.connection.url", "jdbc:hsqldb:mem:aname");
    ac.setProperty("hibernate.connection.username", "sa");
    ac.setProperty("hibernate.dialect", "org.hibernate.dialect.HSQLDialect");
    ac.setProperty("hibernate.hbm2ddl.auto", "create");
    ac.setProperty("hibernate.show_sql", "true");
    ac.setProperty("hibernate.cache.use_second_level_cache", "false");

    ac.addAnnotatedClass(MockAclPermission.class);
    ac.addAnnotatedClass(MockAclObjectIdentity.class);
    ac.addAnnotatedClass(MockSecureEntity.class);

    EntityManagerFactory factory = ac.createEntityManagerFactory();

    EntityManager em = factory.createEntityManager();
    em.getTransaction().begin();

    // Create our mock entity
    MockSecureEntity ent = new MockSecureEntity();
    ent.setId(123);
    em.persist(ent);

    // Now create an identity for it
    MockAclObjectIdentity ident = new MockAclObjectIdentity();
    ident.setId(1);
    ident.setObjectIdentity(new JPAIdentityGenerator().generateIdentity(ent));
    em.persist(ident);

    // And now create some permissions
    /** @todo This step should eventually be done using SeamSecurityManager.grantPermission() */
    MockAclPermission perm = new MockAclPermission();
    perm.setId(1);
    perm.setIdentity(ident);
    perm.setRecipient("testUser");
    perm.setRecipientType(RecipientType.user);
    perm.setMask(0x01 & 0x02);  // read/delete permission only
    em.persist(perm);
    em.flush();
    em.getTransaction().commit();

    // Create an Authentication object in session scope
    MockServletContext ctx = new MockServletContext();
    MockExternalContext eCtx = new MockExternalContext(ctx);

    new Initialization(ctx).setScannerEnabled(false).init();

    Lifecycle.beginRequest(eCtx);

    Contexts.getSessionContext().set("org.jboss.seam.security.authentication",
                                     new UsernamePasswordToken("testUser", "",
                                     new String[] {}));

    Component aclProviderComp = new Component(PersistentAclProvider.class,
                                              "persistentAclProvider");
    PersistentAclProvider aclProvider = (PersistentAclProvider) aclProviderComp.newInstance();
    aclProvider.setPersistenceContextManager(factory);

    /** @todo Under construction */

    //    SeamSecurityManager.instance().set

    Lifecycle.endRequest();
  }
}
