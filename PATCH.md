# PATCH

## Decription
While deploying Bonita Portal in Wildfly (9 and 10) we get an NPE on forwarded request with no query string.
This patch aim is to fix that issue over **restlet 2.3.1** tag.

## Build
- Go into `build` folder
- In build.properties set `verify: false` (Tests do not pass....)
- Launch `ant`
- Patched libs is available: `build/editions/jee/dist/classic/restlet-jee-2.3.1/lib/org.restlet.ext.servlet.jar`

## Links
- https://developer.jboss.org/thread/271688
- https://github.com/restlet/restlet-framework-java/issues/1225
