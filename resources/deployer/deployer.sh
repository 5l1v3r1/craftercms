#!/usr/bin/env bash
DEPLOYER_JAVA_OPTS="$DEPLOYER_JAVA_OPTS "
PID=${DEPLOYER_PID:="crafter-deployer.pid"}
CD_HOME=${CRAFTER_DEPLOYER_HOME:=`pwd`}
OUTPUT=${CRAFTER_DEPLOYER_SDOUT:='crafter-deployer.log'}

function start() {
    if [ -f $CD_HOME/$PID ]; then
        if pgrep -F $CD_HOME/$PID > /dev/null ; then
            echo "Crafter Deployer still running";
            exit -1;
        else
            rm $CD_HOME/$PID
        fi
    fi
    nohup java -jar $JAVA_OPTS "$CD_HOME/crafter-deployer.jar"  > "$CD_HOME/$OUTPUT" >&1&
    echo $! > $CD_HOME/$PID
    exit 0;
}
function stop() {
    kill `cat $CD_HOME/$PID`
    if [ $? -eq 0 ]; then
        rm $CD_HOME/$PID
    fi
    exit 0;
}
function help() {
        echo $(basename $BASH_SOURCE)
        echo "-s --start, Start crafter deployer"
        echo "-k --stop, Stop crafter deployer"
        echo "-d --debug, Implieds start, Start crafter deployer in debug mode"
        exit 0;
}
case $1 in
    -d|--debug)
        set DEPLOYER_JAVA_OPTS = "$DEPLOYER_JAVA_OPTS  -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
        start
    ;;
    -s|--start)
        start
    ;;
    -k|--stop)
        stop
    ;;
    *)
        help
    ;;
esac
