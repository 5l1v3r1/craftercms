#!/usr/bin/env bash

# Script to create the Solr core & Deployer target for a delivery environment.

if [ "$(whoami)" == "root" ]; then
	echo -e "\033[38;5;196m"
	echo -e "Crafter CMS cowardly refuses to run as root."
    echo -e "Running as root is dangerous and is not supported."
    echo -e "\033[0m"
	exit 1
fi

SCRIPT_NAME=$(basename "$0")
function help(){
	echo "$SCRIPT_NAME"
	echo "Arguments:"
	echo -e "\t SITENAME name of the site to be created."
	echo -e "\t REPO_PATH (optional) location of the site content."
  echo -e "\t PRIVATE_KEY (optional) location of the SSH private key."
	echo "Examples:"
	echo -e "\t $SCRIPT_NAME newSite"
	echo -e "\t $SCRIPT_NAME newSite /usr/local/data/repos/sites/newSite/published"
	echo -e "\t $SCRIPT_NAME newSite /usr/local/data/repos/sites/newSite/published /home/admin/.ssh/admin4k"
}
# pre flight check.
if [ -z "$1" ]; then
	help
	exit 2
fi

if [ -n "$2" ]; then
  export AUTHORING_SITE_REPOS=$2
else
  export DELIVERY_HOME=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
  export DELIVERY_ROOT=$( cd "$DELIVERY_HOME/.." && pwd )
  if [ -d "$DELIVERY_ROOT/../crafter-authoring/data/repos/$1/published" ]; then
    export AUTHORING_ROOT=$( cd "$DELIVERY_ROOT/../crafter-authoring" && pwd )
    export AUTHORING_SITE_REPOS=$AUTHORING_ROOT/data/repos/sites
  else
		echo -e "\033[38;5;196m"
		echo -e "Unable to find site $1 default repository path (../crafter-authoring/data/repos/sites/$1/published)."
		echo -e "Location for site $1 repository location is needed."
		echo -e "\033[0m"
		help
		exit 3;
  fi
fi

if [[ ! "$AUTHORING_SITE_REPOS" =~ ^ssh.* ]] && [ ! -d "$AUTHORING_SITE_REPOS" ]; then
    	echo -e "\033[38;5;196m"
    	echo -e " $AUTHORING_SITE_REPOS does not exists or unable to read"
    	echo -e "\033[0m"
    	exit 4
fi

if [ $# -eq 1 ]; then
	SITE=$1
	REPO=$AUTHORING_SITE_REPOS/$SITE/published
	PRIVATE_KEY=""
elif [ $# -eq 2 ]; then
	SITE=$1
	REPO=$2
	PRIVATE_KEY=""
elif [ $# -eq 3 ]; then
  SITE=$1
	REPO=$2
	PRIVATE_KEY=',"ssh_private_key_path":"'"$3"'"'
else
	echo "Usage: init-site.sh <site name> [site's published repo git url] [ssh private key path]"
	exit 1			
fi	

echo "Creating Solr Core"
curl -s -X POST -H "Content-Type: application/json" -d '{"id":"'"$SITE"'"}' "http://localhost:@TOMCAT_HTTP_PORT@/crafter-search/api/2/admin/index/create"
echo ""

echo "Creating Deployer Target"
curl -s -X POST -H "Content-Type: application/json" -d '{"env":"default", "site_name":"'"$SITE"'", "template_name":"remote", "repo_url":"'"$REPO"'", "repo_branch":"live", "engine_url":"http://localhost:@TOMCAT_HTTP_PORT@" '$PRIVATE_KEY' }' "http://localhost:@DEPLOYER_PORT@/api/1/target/create"
echo ""

echo "Done"
