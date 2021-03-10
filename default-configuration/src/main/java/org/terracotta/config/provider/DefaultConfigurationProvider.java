/*
 *
 *  The contents of this file are subject to the Terracotta Public License Version
 *  2.0 (the "License"); You may not use this file except in compliance with the
 *  License. You may obtain a copy of the License at
 *
 *  http://terracotta.org/legal/terracotta-public-license.
 *
 *  Software distributed under the License is distributed on an "AS IS" basis,
 *  WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 *  the specific language governing rights and limitations under the License.
 *
 *  The Covered Software is Terracotta Core.
 *
 *  The Initial Developer of the Covered Software is
 *  Terracotta, Inc., a Software AG company
 *
 */
package org.terracotta.config.provider;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terracotta.config.TCConfigurationParser;
import org.terracotta.config.TcConfiguration;
import org.terracotta.entity.ServiceProviderConfiguration;

import org.terracotta.configuration.Configuration;
import org.terracotta.configuration.ConfigurationException;
import org.terracotta.configuration.ConfigurationProvider;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.terracotta.config.provider.DefaultConfigurationProvider.Opt.CONFIG_PATH;
import com.tc.text.PrettyPrintable;
import com.tc.server.Directories;
import org.terracotta.configuration.FailoverBehavior;
import org.terracotta.configuration.ServerConfiguration;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.terracotta.config.Consistency;
import org.terracotta.config.FailoverPriority;
import org.terracotta.config.Property;
import org.terracotta.config.Server;
import org.terracotta.config.Servers;
import org.terracotta.config.TcProperties;
import static org.terracotta.config.provider.DefaultConfigurationProvider.Opt.SERVER_NAME;
import org.terracotta.config.service.ExtendedConfigParser;
import org.terracotta.config.service.ServiceConfigParser;
import org.terracotta.entity.StateDumpCollector;
import org.terracotta.server.ServerEnv;

public class DefaultConfigurationProvider implements ConfigurationProvider {

  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultConfigurationProvider.class);

  static final String DEFAULT_CONFIG_NAME = "tc-config.xml";

  static final String CONFIG_FILE_PROPERTY_NAME = "tc.config";

  enum Opt {
    SERVER_NAME("n", "name"),
    CONFIG_PATH("f", "config");

    String shortName;
    String longName;

    Opt(String shortName, String longName) {
      Objects.requireNonNull(shortName);
      Objects.requireNonNull(longName);
      this.shortName = shortName;
      this.longName = longName;
    }

    public String getShortName() {
      return shortName;
    }

    public String getLongName() {
      return longName;
    }

    public String getShortOption() {
      return "-" + shortName;
    }

    public String getLongOption() {
      return "--" + longName;
    }
  }

  private volatile String serverName;
  private volatile TcConfiguration configuration;

  @Override
  public void initialize(List<String> configurationParams) throws ConfigurationException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    ClassLoader oldloader = Thread.currentThread().getContextClassLoader();

    try {
      Path configurationPath = getConfiguration(configurationParams.toArray(new String[0])).toAbsolutePath();

      ServerEnv.getServer().console("Attempting to load configuration from the file at '{}'...", configurationPath);

      Thread.currentThread().setContextClassLoader(classLoader);
//  using the service class loader because plugin implementations need to be isolated
//  when grabbing ServiceConfigParsers in xml parsing code through services.
      ClassLoader serviceClassLoader = ServerEnv.getServer().getServiceClassLoader(classLoader, ExtendedConfigParser.class, ServiceConfigParser.class);

      this.configuration = getTcConfiguration(configurationPath, serviceClassLoader);

      ServerEnv.getServer().console("Successfully loaded configuration from the file at '{}'", configurationPath);

      LOGGER.info("The configuration specified by the configuration file at '{}': \n\n{}",
                  configurationPath,
                  configuration.toString());

    } catch (Exception e) {
      throw new ConfigurationException("Unable to initialize DefaultConfigurationProvider with " + configurationParams,
                                       e);
    } finally {
      Thread.currentThread().setContextClassLoader(oldloader);
    }
  }

  @Override
  public Configuration getConfiguration() {
    return new TcConfigurationWrapper(serverName, configuration);
  }

  @Override
  public String getConfigurationParamsDescription() {
    StringWriter stringWriter = new StringWriter();
    try (PrintWriter printWriter = new PrintWriter(stringWriter)) {
      new HelpFormatter().printOptions(printWriter, 100, createOptions(), 4, 4);
    }
    return stringWriter.toString();
  }

  @Override
  public void close() {

  }

  protected TcConfiguration getTcConfiguration(Path configurationPath, ClassLoader serviceClassLoader) throws Exception {
    return TCConfigurationParser.parse(
        Files.newInputStream(configurationPath),
        new ArrayList<>(),
        configurationPath.getParent().toString(),
        serviceClassLoader
    );
  }

  private Path getConfiguration(String[] args) throws Exception {
    CommandLine commandLine = new DefaultParser().parse(createOptions(), args);

    serverName = commandLine.getOptionValue(SERVER_NAME.getShortName());
    String cmdConfigurationFileName = commandLine.getOptionValue(CONFIG_PATH.getShortName());
    if (cmdConfigurationFileName != null && !cmdConfigurationFileName.isEmpty()) {
      Path path = Paths.get(cmdConfigurationFileName);
      if (Files.exists(path)) {
        return path;
      } else {
        String errorMessage = String.format(
            "Specified configuration file '%s' using the '%s' option is not found",
            cmdConfigurationFileName,
            CONFIG_PATH.getShortOption() + "/" + CONFIG_PATH.getLongOption()
        );
        throw new RuntimeException(errorMessage);
      }
    }

    String systemPropertyConfigurationFileName = System.getProperty(CONFIG_FILE_PROPERTY_NAME);
    if (systemPropertyConfigurationFileName != null && !systemPropertyConfigurationFileName.isEmpty()) {
      Path path = Paths.get(systemPropertyConfigurationFileName);
      if (Files.exists(path)) {
        return path;
      } else {
        String errorMessage = String.format(
            "Specified configuration file '%s' using the system property '%s' is not found",
            systemPropertyConfigurationFileName,
            CONFIG_FILE_PROPERTY_NAME
        );
        throw new RuntimeException(errorMessage);
      }
    }

    Path defaultUserDirConfigurationPath = Paths.get(System.getProperty("user.dir")).resolve(DEFAULT_CONFIG_NAME);
    if (Files.exists(defaultUserDirConfigurationPath)) {
      return defaultUserDirConfigurationPath;
    }

    Path defaultUserHomeConfigurationPath = Paths.get(System.getProperty("user.home")).resolve(DEFAULT_CONFIG_NAME);
    if (Files.exists(defaultUserHomeConfigurationPath)) {
      return defaultUserHomeConfigurationPath;
    }

    return Directories.getDefaultConfigFile();
  }

  private static Options createOptions() {
    Options options = new Options();

    options.addOption(
        Option.builder(SERVER_NAME.getShortName())
              .longOpt(SERVER_NAME.getLongName())
              .hasArg()
              .argName("server-name")
              .desc("server name. Default: %h")
              .build()
    );
    options.addOption(
        Option.builder(CONFIG_PATH.getShortName())
              .longOpt(CONFIG_PATH.getLongName())
              .hasArg()
              .argName("config-file")
              .desc("configuration file path")
              .build()
    );

    return options;
  }
  
  public static class TcConfigurationWrapper implements Configuration, PrettyPrintable {
    private final String serverName;
    private final TcConfiguration  configuration;

    public TcConfigurationWrapper(String serverName, TcConfiguration configuration) {
      this.serverName = serverName;
      this.configuration = configuration;
    }

    @Override
    public ServerConfiguration getServerConfiguration() throws ConfigurationException {
      Server defaultServer;
      Servers servers = configuration.getPlatformConfiguration().getServers();
      if (serverName != null) {
        defaultServer = findServer(servers, serverName);
      } else {
        defaultServer = getDefaultServer(servers);
      }
      return new ServerConfigurationImpl(defaultServer, servers.getClientReconnectWindow());
    }

    @Override
    public List<ServerConfiguration> getServerConfigurations() {
      Servers servers = configuration.getPlatformConfiguration().getServers();
      int reconnect = servers.getClientReconnectWindow();
      List<Server> list = servers.getServer();
      List<ServerConfiguration> configs = new ArrayList<>(list.size());
      list.forEach(s->configs.add(new ServerConfigurationImpl(s, reconnect)));
      return configs;
    }

    @Override
    public Properties getTcProperties() {
      TcProperties props = configuration.getPlatformConfiguration().getTcProperties();
        Properties converted = new Properties();
      if (props != null) {
        List<Property> list = props.getProperty();
        list.forEach(p->converted.setProperty(p.getName().trim(), p.getValue().trim()));
      }
      return converted;
    }

    @Override
    public FailoverBehavior getFailoverPriority() {
      FailoverPriority priority = configuration.getPlatformConfiguration().getFailoverPriority();
      if (priority != null) {
        String available = priority.getAvailability();
        Consistency consistent = priority.getConsistency();
        if (consistent != null) {
          int votes = (consistent.getVoter() != null) ? consistent.getVoter().getCount() : 0;
          return new FailoverBehavior(FailoverBehavior.Type.CONSISTENCY, votes);
        } else {
          if (available == null) {
            return null;
          } else {
            return new FailoverBehavior(FailoverBehavior.Type.AVAILABILITY, 0);
          }
        }
      }
      return null;
    }

    @Override
    public List<ServiceProviderConfiguration> getServiceConfigurations() {
      return this.configuration.getServiceConfigurations();
    }

    @Override
    public <T> List<T> getExtendedConfiguration(Class<T> type) {
      return this.configuration.getExtendedConfiguration(type);
    }

    @Override
    public String getRawConfiguration() {
      ClassLoader loader = Thread.currentThread().getContextClassLoader();
      try {
        Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
        return configuration.toString();
      } finally {
        Thread.currentThread().setContextClassLoader(loader);
      }
    }
    
    @Override
    public Map<String, ?> getStateMap() {
      Map<String, Object> mappedStateCollector = new LinkedHashMap<>();
      this.configuration.addStateTo(createStateDumpCollector("", mappedStateCollector));
      return mappedStateCollector;
    }

    private StateDumpCollector createStateDumpCollector(String name, Map<String, Object> store) {
      return new StateDumpCollector() {
        @Override
        public StateDumpCollector subStateDumpCollector(String sub) {
          return createStateDumpCollector(name + sub + ".", store);
        }

        @Override
        public void addState(String key, Object value) {
          store.put(name + key, value);
        }
      };
    }
    
    private Server getDefaultServer(Servers servers) throws ConfigurationException {
      List<Server> serverList = servers.getServer();
      if (serverList.size() == 1) {
        return serverList.get(0);
      }

      try {
        Set<InetAddress> allLocalInetAddresses = getAllLocalInetAddresses();
        Server defaultServer = null;
        for (Server server : serverList) {
          if (allLocalInetAddresses.contains(InetAddress.getByName(server.getHost()))) {
            if (defaultServer == null) {
              defaultServer = server;
            } else {
              throw new ConfigurationException("You have not specified a name for your Terracotta server, and" + " there are "
                                                    + serverList.size() + " servers defined in the Terracotta configuration file. "
                                                    + "The script can not automatically choose between the following server names: "
                                                    + defaultServer.getName() + ", " + server.getName()
                                                    + ". Pass the desired server name to the script using " + "the -n flag.");

            }
          }
        }
        return defaultServer;
      } catch (UnknownHostException uhe) {
        throw new ConfigurationException("Exception when trying to find the default server configuration", uhe);
      }
    }
    
    private Set<InetAddress> getAllLocalInetAddresses() {
      Set<InetAddress> localAddresses = new HashSet<>();
      Enumeration<NetworkInterface> networkInterfaces;
      try {
        networkInterfaces = NetworkInterface.getNetworkInterfaces();
      } catch (SocketException e) {
        throw new RuntimeException(e);
      }
      while (networkInterfaces.hasMoreElements()) {
        Enumeration<InetAddress> inetAddresses = networkInterfaces.nextElement().getInetAddresses();
        while (inetAddresses.hasMoreElements()) {
          localAddresses.add(inetAddresses.nextElement());
        }
      }
      return localAddresses;
    }
  }
  
  private static Server findServer(Servers servers, String serverName) throws ConfigurationException {
    for (Server server : servers.getServer()) {
      if (server.getName().equals(serverName)) {
        return server;
      }
    }
    throw new ConfigurationException("You have specified server name '" + serverName
                                          + "' which does not exist in the specified configuration. \n\n"
                                          + "Please check your settings and try again.");
  }
}
