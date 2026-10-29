@echo off
rem Keep this terminal open while testing payments. No live mode.
stripe.cmd listen --events checkout.session.completed,checkout.session.expired,checkout.session.async_payment_succeeded,checkout.session.async_payment_failed,invoice.paid,invoice.payment_failed,customer.subscription.created,customer.subscription.updated,customer.subscription.deleted,customer.subscription.paused,customer.subscription.resumed --forward-to http://127.0.0.1:8081/api/billing/webhook
