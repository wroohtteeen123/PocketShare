import io.pocketshare.AccountPolicy;
import io.pocketshare.ShareAccount;
import io.pocketshare.AccountDiagnostics;
import java.util.*;

public class TestAccountPolicy {
    static void check(boolean b) { if (!b) throw new AssertionError(); }
    static ShareAccount user(String name, int id, boolean enabled, boolean write) {
        return new ShareAccount(name, id, enabled, write, "encrypted");
    }
    public static void main(String[] args) {
        var p = AccountPolicy.INSTANCE;
        var d = AccountDiagnostics.INSTANCE;
        check(!d.sanitize("failure secret'密码", "secret'密码").contains("secret'密码"));
        check(!d.sanitize("hash 0123456789abcdef0123456789abcdef", "test").contains("0123456789abcdef"));
        check(d.sanitize("Permission denied", "test").equals("Permission denied"));
        check(!d.sanitize("x".repeat(7000) + "private-password", "private-password").contains("private-password"));
        check(d.sanitize("", "").contains("No subprocess"));
        check(p.guestMapping(true).equals("map to guest = Bad User"));
        check(p.guestMapping(false).equals("map to guest = Never"));
        for (String bad : new String[]{"root", "guest", "nobody", "-bad", "a b", "a\nwrite list = root", "@admins", "a%U", "", "alice,root"}) check(!p.validName(bad));
        check(p.validName("alice_2")); check(!p.validPassword("one\ntwo")); check(p.validPassword("safe'密码"));
        var alice = user("alice", 200000000, true, false);
        var bob = user("bob", 200000001, true, true);
        var disabled = user("disabled", 200000002, false, true);
        var users = Arrays.asList(alice, bob, disabled);
        check(p.config(users, false, true).equals("read only = yes\nvalid users = alice bob\nwrite list = bob"));
        check(p.config(users, true, false).endsWith("write list = bob"));
        check(p.config(users, true, true).endsWith("write list = bob root"));
        check(p.config(Collections.singletonList(alice), false, true).endsWith("write list = "));
        check(!p.config(Collections.singletonList(bob), false, true).contains("alice"));
        check(p.config(Collections.emptyList(), true, false).contains("valid users = root"));
        check(p.config(Collections.emptyList(), true, false).endsWith("write list = "));
        check(p.config(Collections.emptyList(), true, true).endsWith("write list = root"));
        try { p.config(Collections.emptyList(), false, true); throw new AssertionError(); } catch (IllegalArgumentException expected) {}
        try { p.validate(Arrays.asList(alice, alice)); throw new AssertionError(); } catch (IllegalArgumentException expected) {}
        try { p.validate(Arrays.asList(alice, user("bob", alice.getUid(), true, true))); throw new AssertionError(); } catch (IllegalArgumentException expected) {}
        System.out.println("PASS: allow-list, read-only, per-user write, guest separation, disabled/deleted users, duplicate identity and injection rejection");
    }
}
