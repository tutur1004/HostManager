package fr.milekat.hostmanager.hosts.pterodactyl;

import fr.milekat.hostmanager.Main;
import fr.milekat.hostmanager.api.classes.Instance;
import fr.milekat.hostmanager.hosts.exeptions.HostExecuteException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;

public class PterodactylRequests extends HttpExecute {
    private static final String ENDPOINT = Main.getFileConfig().getString("host.pterodactyl.endpoint");
    private static final String KEY = Main.getFileConfig().getString("host.pterodactyl.admin.key");

    /**
     * Create a pterodactyl server
     * @param instance representation of the desired server
     */
    public static JSONObject setupServer(Instance instance) throws HostExecuteException {
        try {
            if (instance.getPort()==0) {
                // TODO: 25/05/2022 storage find available port
            }
            JSONObject server = execute(new URL(ENDPOINT + "/api/application/servers"), "POST", KEY,
                    new JSONObject().put("name", instance.getName())
                            .put("description", instance.getDescription())
                            .put("user", Main.getFileConfig().getString("host.pterodactyl.account.id"))
                            .put("egg", Main.getFileConfig().getString("host.pterodactyl.egg"))
                            .put("docker_image", instance.getGame().getImage())
                            .put("startup", Main.getFileConfig().getString("host.command")
                                    .replaceAll("\\{MEM}", String.valueOf(instance.getGame().getRequirements())))
                            .put("environment", new JSONObject()
                                    .put(Main.HOST_UUID_ENV_VAR_NAME, instance.getHost().getUuid()))
                            .put("limits", new JSONObject().put("memory", instance.getGame().getRequirements())
                                    .put("swap", 0).put("disk", 1024).put("io", 500).put("cpu", 0))
                            .put("feature_limits", new JSONObject().put("databases", 0).put("backups", 0))
                            .put("allocation", new JSONObject().put("default", setupAllocation(instance.getPort())))
                            .put("start_on_completion", true)
                            .toString());
            return server;
        } catch (IOException throwable) {
            if (Main.DEBUG) {
                throwable.printStackTrace();
            }
            throw new HostExecuteException(throwable, "Pterodactyl API error, URL IOException ?");
        } catch (AllocationAlreadyUsed throwable) {
            if (Main.DEBUG) {
                throwable.printStackTrace();
            }
            throw new HostExecuteException(throwable, "Pterodactyl API error, allocation port already used.");
        }
    }

    /**
     * Create an allocation on this port
     * @param port allocation port
     * @return allocation id
     * @throws AllocationAlreadyUsed if ...
     */
    public static int setupAllocation(int port) throws AllocationAlreadyUsed, HostExecuteException {
        // TODO: 07/05/2022 Create allocation and return allocation id
        String ip = "127.0.0.1";
        String node = Main.getFileConfig().getString("host.pterodactyl.node");

        //  Try to retrieve this allocation (If already exist)
        int allocation = retrieveAllocation(ip, port, node);
        if (allocation !=0) return allocation;

        //  If not, create this allocation
        try {
            execute(new URL(ENDPOINT + "/api/application/nodes/" + node + "/allocations"), "POST", KEY,
                new JSONObject().put("ip", ip).put("ports", Collections.singleton(port)).toString());
            return retrieveAllocation(ip, port, node);
        } catch (IOException throwable) {
            throw new AllocationAlreadyUsed(throwable); // TODO: 24/05/2022 Check this point
        }
    }

    /**
     * Retrieve an allocation if exist
     * @param ip allocation ip
     * @param port allocation port
     * @param node allocation node name
     * @return allocation id
     * @throws AllocationAlreadyUsed Port is already assign or connection issue
     */
    private static int retrieveAllocation(String ip, int port, String node) throws AllocationAlreadyUsed {
        try {
            JSONObject allocations = execute(new URL(ENDPOINT + "/api/application/nodes/" + node + "/allocations"),
                    "POST", KEY, "");
            for (Object loop : allocations.getJSONArray("data")) {
                JSONObject jsonAllocation = new JSONObject(loop);
                if (jsonAllocation.getString("attributes.ip").equalsIgnoreCase(ip) &&
                        jsonAllocation.getInt("attributes.port") == port) {
                    if (jsonAllocation.getBoolean("attributes.assigned")) {
                        throw new AllocationAlreadyUsed(new Throwable("Port already assign"));
                    }
                    return jsonAllocation.getInt("attributes.id");
                }
            }
            return 0;
        } catch (HostExecuteException | IOException throwable) {
            throw new AllocationAlreadyUsed(throwable);
        }
    }

    private static class AllocationAlreadyUsed extends Exception {
        public AllocationAlreadyUsed(Throwable throwable) {
            super(throwable);
        }
    }
}