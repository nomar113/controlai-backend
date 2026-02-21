#!/bin/sh
set -e

awslocal sqs create-queue --queue-name purchase-from-notification
