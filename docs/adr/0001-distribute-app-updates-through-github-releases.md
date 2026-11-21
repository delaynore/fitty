# Distribute Fitty updates through GitHub Releases

Fitty already distributes signed APKs directly. Publish them as stable releases in the public `delaynore/fitty` repository so installed copies can check for updates without a GitHub account or a separate server. Keep release signing and publication manual.

Fitty checks GitHub Releases on request, offers a build with a higher Android version code, and opens Android's installer after the lifter taps Update. Keep the existing application ID and signing key so Android can replace the installed app and keep its training data. Installed copies will continue to check this repository if the project moves to another distribution channel.
