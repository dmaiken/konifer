import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigRenderOptions;
import io.ktor.server.config.HoconApplicationConfig;
import java.io.File;

class HoconProbe {
    public static void main(String[] args) {
        var config = ConfigFactory.parseFile(
            new File(args[0]), ConfigParseOptions.defaults().setAllowMissing(false)
        ).resolve();
        var application = new HoconApplicationConfig(config);
        for (var key : new String[] {
            "object-store.s3.endpoint-url", "object-store.s3.access-key",
            "object-store.s3.secret-key", "data-store.postgresql.ssl-root-cert",
            "data-store.postgresql.password"
        }) {
            if (config.hasPath(key)) throw new IllegalStateException("Stale connection setting: " + key);
            if (application.propertyOrNull(key) != null) throw new IllegalStateException("Stale Ktor setting: " + key);
        }
        System.out.println(config.root().render(ConfigRenderOptions.concise().setJson(true)));
    }
}
