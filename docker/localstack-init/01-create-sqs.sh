#!/bin/sh
set -e

awslocal sqs create-queue --queue-name payments-notifications
awslocal sqs create-queue --queue-name purchases-invoices
