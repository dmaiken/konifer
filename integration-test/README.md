
# Integration tests

These tests are designed to ensure that the docker image contains all the necessary runtime dependencies 
for supporting Konifer features. Not all things have to be tested, and the assertions must be granular, or it should be
a functional test instead.

The goal here is to test aspects of the platform that may change when the runtime changes from a local development 
machine to a constructed docker image (networking, libvips runtimes, etc.).

