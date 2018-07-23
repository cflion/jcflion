package com.jcflion;

import com.jcflion.gray.GrayConfigManager;
import com.jcflion.util.CollectionUtil;
import com.jcflion.util.Constant;
import com.jcflion.util.StringUtil;
import com.coreos.jetcd.Client;
import com.coreos.jetcd.Watch;
import com.coreos.jetcd.common.exception.EtcdException;
import com.coreos.jetcd.data.ByteSequence;
import com.coreos.jetcd.watch.WatchEvent;
import com.coreos.jetcd.watch.WatchResponse;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author kanner
 */
public class InitConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(InitConfig.class);

    private String app;

    private String env;

    private String endpoint;

    private final AtomicBoolean inited = new AtomicBoolean(false);

    public InitConfig() {

    }

    public InitConfig(String app, String env, String endpoint) {
        this.app = app;
        this.env = env;
        this.endpoint = endpoint;
    }

    public void init() {
        if (StringUtil.isEmpty(app)) {
            app = System.getProperty(Constant.CFLION_APP_NAME, System.getenv(Constant.CFLION_APP_NAME));
        }
        if (StringUtil.isEmpty(env)) {
            env = System.getProperty(Constant.APP_ENV, System.getenv(Constant.APP_ENV));
        }
        if (StringUtil.isEmpty(endpoint)) {
            endpoint = System.getProperty(Constant.CFLION_ENDPOINT, System.getenv(Constant.CFLION_ENDPOINT));
        }
        if (StringUtil.isEmpty(app) || StringUtil.isEmpty(endpoint)) {
            LOGGER.error("init config fail, because params: [app, endpoint] are empty");
            return;
        }
        if (StringUtil.isEmpty(env)) {
            env = "dev";
        }
        String path = "/v1/watchers";
        if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
            endpoint = "http://" + endpoint;
        }
        if (endpoint.endsWith("/")) {
            path = "v1/watchers";
        }
        final String url = endpoint + path;
        String[] etcdEndpoints = null;
        String key = null;
        try {
            final HttpResponse<ResponseRet> resp = Unirest.get(url).queryString("app", app).queryString("env", env).asObject(ResponseRet.class);
            if (resp.getStatus() == 200) {
                final ResponseRet responseRet = resp.getBody();
                if (null != responseRet.getData()) {
                    final WatcherRet watcherRet = responseRet.getData();
                    etcdEndpoints = watcherRet.getEndpoints();
                    key = watcherRet.getKey();
                }
            } else {
                LOGGER.error("init config fail when retrieve etcd endpoint, status={}, body={}", resp.getStatus(), resp.getBody());
                return;
            }
        } catch (UnirestException e) {
            LOGGER.error("init config error", e);
            return;
        }
        if (CollectionUtil.isEmpty(etcdEndpoints) || StringUtil.isEmpty(key)) {
            LOGGER.error("init config fail, because [key={}] and [etcd endpoints={}] illegal", key, etcdEndpoints);
            return;
        }
        if (!inited.compareAndSet(false, true)) {
            LOGGER.warn("app={} env={} has already init", app, env);
            return;
        }
        startWatch(etcdEndpoints, key);
    }

    public void startWatch(String[] etcdEndpoints, String key) {
        new Thread(() -> {
            Client client = Client.builder().endpoints(etcdEndpoints).build();
            Watch watch = client.getWatchClient();
            Watch.Watcher watcher = watch.watch(ByteSequence.fromString(key));
            while (true) {
                try {
                    final WatchResponse watchResponse = watcher.listen();
                    for (final WatchEvent watchEvent : watchResponse.getEvents()) {
                        final String value = Optional.ofNullable(watchEvent.getKeyValue().getValue()).map(ByteSequence::toStringUtf8).orElse("");
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("etcd watch type={}, key={}, value={}",
                                    watchEvent.getEventType(),
                                    Optional.ofNullable(watchEvent.getKeyValue().getKey()).map(ByteSequence::toStringUtf8).orElse(""),
                                    value
                            );
                        }
                        if (watchEvent.getEventType().equals(WatchEvent.EventType.PUT)) {
                            if (StringUtil.isNotEmpty(value)) {
                                ConfigManager.reloadConfigContent(value);
                                GrayConfigManager.resetGrayConfigCache();
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    LOGGER.error("watch interrupted [etcd endpoints={}] [key={}]", e);
                    continue;
                } catch (EtcdException e) {
                    LOGGER.error("watch occurs connection error, [error code={}] reconnect...", e.getErrorCode());
                    client.close();
                    watch.close();
                    watcher.close();
                    client = Client.builder().endpoints(etcdEndpoints).build();
                    watch = client.getWatchClient();
                    watcher = watch.watch(ByteSequence.fromString(key));
                }
            }
        }).start();
    }

    public void setApp(String app) {
        this.app = app;
    }

    public void setEnv(String env) {
        this.env = env;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }
}

class ResponseRet {

    String msg;

    WatcherRet data;

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public WatcherRet getData() {
        return data;
    }

    public void setData(WatcherRet data) {
        this.data = data;
    }

}

class WatcherRet {

    String key;

    String[] endpoints;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String[] getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(String[] endpoints) {
        this.endpoints = endpoints;
    }
}

