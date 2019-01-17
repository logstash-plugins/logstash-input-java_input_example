# Logstash Java Plugin

[![Travis Build Status](https://travis-ci.org/logstash-plugins/logstash-input-java_input_example.svg)](https://travis-ci.org/logstash-plugins/logstash-input-java_input_example)

This is a Java plugin for [Logstash](https://github.com/elastic/logstash).

It is fully free and fully open source. The license is Apache 2.0, meaning you are free to use it however you want.

## How to write a Java input

> <b>IMPORTANT NOTE:</b> Native support for Java plugins in Logstash is in the experimental phase. While unnecessary
changes will be avoided, anything may change in future phases. See the ongoing work on the 
[beta phase](https://github.com/elastic/logstash/pull/10232) of Java plugin support for the most up-to-date status.

### Overview 

Native support for Java plugins in Logstash consists of several components including:
* Extensions to the Java execution engine to support running Java plugins in Logstash pipelines
* APIs for developing Java plugins. The APIs are in the `co.elastic.logstash.api` package. If a Java plugin 
references any classes or specific concrete implementations of API interfaces outside that package, breakage may 
occur because the implementation of classes outside of the API package may change at any time.
* Tooling to automate the packaging and deployment of Java plugins in Logstash [not complete as of the experimental phase]

To develop a new Java input for Logstash, you write a new Java class that conforms to the Logstash Java Input
API, package it, and install it with the `logstash-plugin` utility. We'll go through each of those steps in this guide.

### Coding the plugin

It is recommended that you start by copying the 
[example input plugin](https://github.com/logstash-plugins/logstash-input-java_input_example). The example input
plugin generates a configurable number of simple events before terminating. Let's look at the main class in that 
example input:
 
```java
@LogstashPlugin(name="java_input_example")
public class JavaInputExample implements Input {

    public static final PluginConfigSpec<Long> EVENT_COUNT_CONFIG =
            Configuration.numSetting("count", 3);

    public static final PluginConfigSpec<String> PREFIX_CONFIG =
            Configuration.stringSetting("prefix", "message");

    private long count;
    private String prefix;
    private final CountDownLatch done = new CountDownLatch(1);
    private volatile boolean stopped;

    public JavaInputExample(Configuration config, Context context) {
        count = config.get(EVENT_COUNT_CONFIG);
        prefix = config.get(PREFIX_CONFIG);
    }

    @Override
    public void start(QueueWriter queueWriter) {
        int eventCount = 0;
        try {
            while (!stopped && eventCount < count) {
                eventCount++;
                queueWriter.push(Collections.singletonMap("message",
                        prefix + " " + StringUtils.center(eventCount + " of " + count, 20)));
            }
        } finally {
            stopped = true;
            done.countDown();
        }
    }

    @Override
    public void stop() {
        stopped = true; // set flag to request cooperative stop of input
    }

    @Override
    public void awaitStop() throws InterruptedException {
        done.await(); // blocks until input has stopped
    }

    @Override
    public Collection<PluginConfigSpec<?>> configSchema() {
        return Arrays.asList(EVENT_COUNT_CONFIG, PREFIX_CONFIG);
    }
}
```

Let's step through and examine each part of that class.

#### Class declaration
```java
@LogstashPlugin(name="java_input_example")
public class JavaInputExample implements Input {
```
There are two things to note about the class declaration:
* All Java plugins must be annotated with the `@LogstashPlugin` annotation. Additionally:
  * The `name` property of the annotation must be supplied and defines the name of the plugin as it will be used
   in the Logstash pipeline definition. For example, this input would be referenced in the input section of the
   Logstash pipeline defintion as `input { java_input_example => { .... } }`
  * The value of the `name` property must match the name of the class excluding casing and underscores.
* The class must implement the `co.elastic.logstash.api.v0.Input` interface.

#### Plugin settings

The snippet below contains both the setting definition and the method referencing it:
```java
public static final PluginConfigSpec<Long> EVENT_COUNT_CONFIG =
        Configuration.numSetting("count", 3);

public static final PluginConfigSpec<String> PREFIX_CONFIG =
        Configuration.stringSetting("prefix", "message");

@Override
public Collection<PluginConfigSpec<?>> configSchema() {
    return Arrays.asList(EVENT_COUNT_CONFIG, PREFIX_CONFIG);
}
```
The `PluginConfigSpec` class allows developers to specify the settings that a plugin supports complete with setting 
name, data type, deprecation status, required status, and default value. In this example, the `count` setting defines
the number of events that will be generated and the `prefix` setting defines an optional prefix to include in the
event field. Neither setting is required and if it is not explicitly set, the settings default to `3` and 
`message`, respectively.

The `configSchema` method must return a list of all settings that the plugin supports. In a future phase of the
Java plugin project, the Logstash execution engine will validate that all required settings are present and that
no unsupported settings are present.

#### Constructor and initialization
```java
private long count;
private String prefix;

public JavaInputExample(Configuration config, Context context) {
    count = config.get(EVENT_COUNT_CONFIG);
    prefix = config.get(PREFIX_CONFIG);
}
```
All Java input plugins must have a constructor taking both a `Configuration` and `Context` argument. This is the
constructor that will be used to instantiate them at runtime. The retrieval and validation of all plugin settings
should occur in this constructor. In this example, the values of the two plugin settings are retrieved and
stored in local variables for later use in the `start` method. 

Any additional initialization may occur in the constructor as well. If there are any unrecoverable errors encountered
in the configuration or initialization of the input plugin, a descriptive exception should be thrown. The exception
will be logged and will prevent Logstash from starting.

#### Start method
```java
@Override
public void start(QueueWriter queueWriter) {
    int eventCount = 0;
    try {
        while (!stopped && eventCount < count) {
            eventCount++;
            queueWriter.push(Collections.singletonMap("message",
                    prefix + " " + StringUtils.center(eventCount + " of " + count, 20)));
        }
    } finally {
        stopped = true;
        done.countDown();
    }
}
```
The `start` method begins the event-producing loop in an input. Inputs are flexible and may produce events through
many different mechanisms including:

 * a pull mechanism such as periodic queries of external database</li>
 * a push mechanism such as events sent from clients to a local network port</li>
 * a timed computation such as a heartbeat</li>

or any other mechanism that produces a useful stream of events. Event streams may be either finite or infinite. 
If the input produces an infinite stream of events, this method should loop until a stop request is made through
the `stop` method. If the input produces a finite stream of events, this method should terminate when the last 
event in the stream is produced or a stop request is made, whichever comes first.

Events should be constructed as instances of `Map<String, Object>` and pushed into the event pipeline via the
`QueueWriter.push()` method. 

#### Stop and awaitStop methods

```java
private final CountDownLatch done = new CountDownLatch(1);
private volatile boolean stopped;

@Override
public void stop() {
    stopped = true; // set flag to request cooperative stop of input
}

@Override
public void awaitStop() throws InterruptedException {
    done.await(); // blocks until input has stopped
}
```
The `stop` method notifies the input to stop producing events. The stop mechanism may be implemented in any way
that honors the API contract though a `volatile boolean` flag works well for many use cases.

Inputs stop both asynchronously and cooperatively. Use the `awaitStop` method to block until the input has 
completed the stop process. Note that this method should **not** signal the input to stop as the `stop` method 
does. The awaitStop mechanism may be implemented in any way that honors the API contract though a `CountDownLatch`
works well for many use cases.

#### Unit tests
Lastly, but certainly not least importantly, unit tests are strongly encouraged. The example input plugin includes
an [example unit test](https://github.com/logstash-plugins/logstash-input-java_input_example/blob/master/src/test/java/org/logstash/javaapi/JavaInputExampleTest.java)
that you can use as a template for your own.

### Packaging and deployment

For the purposes of dependency management and interoperability with Ruby plugins, Java plugins will be packaged
as Ruby gems. One of the goals for Java plugin support is to eliminate the need for any knowledge of Ruby or its
toolchain for Java plugin development. Future phases of the Java plugin project will automate the packaging of
Java plugins as Ruby gems so no direct knowledge of or interaction with Ruby will be required. In the experimental
phase, Java plugins must still be manually packaged as Ruby gems and installed with the `logstash-plugin` utility.

#### Compile to JAR file

The Java plugin should be compiled and assembled into a fat jar with the `vendor` task in the Gradle build file. This
will package all Java dependencies into a single jar and write it to the correct folder for later packaging into
a Ruby gem.

#### Manual packaging as Ruby gem 

Several Ruby source files are required to correctly package the jar file as a Ruby gem. These Ruby files are used
only at Logstash startup time to identify the Java plugin and are not used during runtime event processing. In a 
future phase of the Java plugin support project, these Ruby source files will be automatically generated. 

`logstash-input-<input-name>.gemspec`
```
Gem::Specification.new do |s|
  s.name            = 'logstash-input-java_input_example'
  s.version         = '0.0.1'
  s.licenses        = ['Apache-2.0']
  s.summary         = "Example input using Java plugin API"
  s.description     = ""
  s.authors         = ['Elasticsearch']
  s.email           = 'info@elastic.co'
  s.homepage        = "http://www.elastic.co/guide/en/logstash/current/index.html"
  s.require_paths = ['lib', 'vendor/jar-dependencies']

  # Files
  s.files = Dir["lib/**/*","spec/**/*","*.gemspec","*.md","CONTRIBUTORS","Gemfile","LICENSE","NOTICE.TXT", "vendor/jar-dependencies/**/*.jar", "vendor/jar-dependencies/**/*.rb", "VERSION", "docs/**/*"]

  # Special flag to let us know this is actually a logstash plugin
  s.metadata = { 'logstash_plugin' => 'true', 'logstash_group' => 'input'}

  # Gem dependencies
  s.add_runtime_dependency "logstash-core-plugin-api", ">= 1.60", "<= 2.99"
  s.add_runtime_dependency 'jar-dependencies'

  s.add_development_dependency 'logstash-devutils'
end
```
The above file can be used unmodified except that `s.name` must follow the `logstash-input-<input-name>` pattern
and `s.version` must match the `project.version` specified in the `build.gradle` file.

`lib/logstash/inputs/<input-name>.rb`
```
# encoding: utf-8
require "logstash/inputs/base"
require "logstash/namespace"
require "logstash-input-java_input_example_jars"
require "java"

class LogStash::Inputs::JavaInputExample < LogStash::Inputs::Base
  config_name "java_input_example"

  def self.javaClass() org.logstash.javaapi.JavaInputExample.java_class; end
end
```
The following items should be modified in the file above:
1. It should be named to correspond with the input name.
1. `require "logstash-input-java_input_example_jars"` should be changed to reference the appropriate "jars" file
as described below.
1. `class LogStash::Inputs::JavaFilterExample < LogStash::Inputs::Base` should be changed to provide a unique and
descriptive Ruby class name.
1. `config_name "java_input_example"` must match the name of the plugin as specified in the `name` property of
the `@LogstashPlugin` annotation.
1. `def self.javaClass() org.logstash.javaapi.JavaInputExample.java_class; end` must be modified to return the
class of the Java input.

`lib/logstash-input-<input-name>_jars.rb`
```
require 'jar_dependencies'
require_jar('org.logstash.javaapi', 'logstash-input-java_input_example', '0.0.1')
```
The following items should be modified in the file above:
1. It should be named to correspond with the input name.
1. The `require_jar` directive should be modified to correspond to the `group` specified in the Gradle build file,
the name of the input JAR file, and the version as specified in both the gemspec and Gradle build file.

Once the above files have been properly created along with the plugin JAR file, the gem can be built with the
following command:
```
gem build logstash-input-<input-name>.gemspec
``` 

#### Installing the Java plugin in Logstash

Once your Java plugin has been packaged as a Ruby gem, it can be installed in Logstash with the following command:
```
bin/logstash-plugin install --no-verify --local /path/to/javaPlugin.gem
```
Substitute backslashes for forward slashes as appropriate in the command above for installation on Windows platforms. 

### Feedback

If you have any feedback on Java plugin support in Logstash, please comment on our 
[main Github issue](https://github.com/elastic/logstash/issues/9215) or post in the 
[Logstash forum](https://discuss.elastic.co/c/logstash).