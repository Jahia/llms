#!/bin/bash
# Exports the settings of the e2e stack into the shell, which is where Cypress
# and docker compose read them from. Source it, do not execute it:
#
#   source set-env.sh
#
# The ci.* and env.* wrappers source it themselves, so it is only needed by hand
# before `docker compose up`, `yarn e2e:ci` or `yarn e2e:debug`, and again in
# every new terminal.
#
# Every value is ${NAME:-default}: anything already exported wins, which is how
# CI passes its own values and how you override one for a single run:
#
#   JAHIA_IMAGE=jahia/jahia-ee:8.2.3.2 JAHIA_HOST_PORT=8480 source set-env.sh
#
# A git-ignored .env beside this file is sourced first when it exists; that is
# where a license goes. The credentials below are fixtures of the throwaway
# test instance, never real ones.

# shellcheck source=/dev/null
[[ -f .env ]] && source .env

# The module under test. env.run.sh checks it is installed before running a spec.
export MODULE_ID=${MODULE_ID:-llms}

# Jahia 8.2 or later. The EE image wants a license unless it carries one.
export JAHIA_VERSION=${JAHIA_VERSION:-LATEST}
export JAHIA_IMAGE=${JAHIA_IMAGE:-ghcr.io/jahia/jahia-ee-dev:8-SNAPSHOT}
export JAHIA_LICENSE=${JAHIA_LICENSE:-""}
# Where the specs reach Jahia. From your own machine: http://localhost:<JAHIA_HOST_PORT>.
export JAHIA_URL=${JAHIA_URL:-http://jahia:8080}
export JAHIA_HOST=${JAHIA_HOST:-jahia}
export JAHIA_PORT=${JAHIA_PORT:-8080}
# The port of your machine the Jahia container is published on.
export JAHIA_HOST_PORT=${JAHIA_HOST_PORT:-8080}
export JAHIA_USERNAME=${JAHIA_USERNAME:-root}
export JAHIA_PASSWORD=${JAHIA_PASSWORD:-root1234}
export SUPER_USER_PASSWORD=${SUPER_USER_PASSWORD:-root1234}

# The tests container, built by ci.build.sh.
export TESTS_IMAGE=${TESTS_IMAGE:-llms-tests:latest}

# Applied to the instance before the suite runs.
export MANIFEST=${MANIFEST:-provisioning-manifest-build.yml}

# Jahia Nexus, for modules resolved from mvn: coordinates.
export NEXUS_USERNAME=${NEXUS_USERNAME:-}
export NEXUS_PASSWORD=${NEXUS_PASSWORD:-}

export TIMEZONE=${TIMEZONE:-UTC}
export SKIP_TESTS=${SKIP_TESTS:-false}
export TESTS_PROFILE=${TESTS_PROFILE:-}
