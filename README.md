## The challenge

As most "Software as a Service" (SaaS) companies, Pleo needs to charge a subscription fee every month. Our database contains a few invoices for the different markets in which we operate. Your task is to build the logic that will pay those invoices on the first of the month. While this may seem simple, there is space for some decisions to be taken and you will be expected to justify them.

### Data consistency

FinTech software often needs to deal with money. Be it charge customers and keep track of that payments, transfer funds between users, or other functionality, there is always one common challenge to face - data consistency. It is catastrophic if transfer of funds is interrupted by network error and database is left in inconsistent state, e.g. substracting funds from first account, but not adding them to the second one. The consistency can also be challenged when multiple operations are executed concurrently. To deal with that software must be designed is a way to ensure consistency in all scenarios.

### The problem

The challenge expects code handling charging customers' invoices to be written. External interface is provided, that mocks charging the customer's e.g. credit card and returns boolean stating if transaction succeeded, or throws in cases of e.g. network connectivity errors. If such network error occures, it is unknown if charging of customer's credit card succeeded. It is equaly possible that the transfer failed, or that the transfer succeeded and just the confirmation got lost on the way to our service. Also, concurrent execution of 2+ threads/instances/etc. trying to perform the same operation at the same time must be handled as well.

## The solution

### REST API

I added an `POST /rest/v1/invoices/payments` endpoint to REST API that will find all invoices that are in `PENDING` state and try to charge each of them. Since every month new invoices are created for customers, this endpoint should be called periodically e.g. by task scheduler to keep paying invoices as they come. I assumed that invoices will be created on first day of the month by another service, so I focused on paying them as they appear.

Of cource there is more room to make this mechanism more sophisticated. Introducing list of failed/succeeded transactions, retrying failed transactions a few times with expotential retry mechanism, introducing ServiceBus queue with invoices to charge and multiple workers to consume from it, sending notifications if invoice failed to be paid for a few times, etc. I decided to focus on BillingService logic, leaving this part simple but reasonable.

### BillingService

To handle concurrent execution cases in my solution I rely on SQL database constraints. I was given an `Invoices` database table already, containing all invoices. I added another table, `InvoicePayments`, with 2 fields:

- invoiceId - that is both Foreign Key reference to `Invoices`, as well as PrimaryKey for `InvoicePayments`
- status - can be either `IN_PROGRESS` or `PAID`

Since `invoiceId` is PrimaryKey, all it's values must be unique as per [SQLite docs](https://www.sqlite.org/lang_createtable.html#constraints): `Each row in a table with a primary key must have a unique combination of values in its primary key columns.`. I use this constraint to ensure that only one payment can be in progress at the same time for a single invoice.

When invoice is first created, it gets `PENDING` status. That means it is due to be paid and no payment is currently in progress. This status is defined as `Invoice` that has no related `InvoicePayment`, i.e. There is no `InvoicePayment` in the DB with `invoiceId` set to this `Invoice` id.

When `BillingService.chargeInvoice` is called on an invoice, the code tries to create new row in `InvoicePayments` with `invoiceId` set to id of given invoice, and `status` set to `IN_PROGRESS`. This operation can only succeed if there was no row in `InvoicePayment` before with the same `invoiceId` (due to PrimaryKey constraint). The code checks if operation succeeded. If yes, the code has now exclusivity on trying to charge the user using `paymentProvider`. If concurrently executed code calls `BillingService.chargeInvoice`, it will not be able to create `InvoicePayment` and will fail.

If `paymentProvider` succeeds, the code then will set `InvoicePayment` status to `PAID` and finish execution. This way no code can ever execute the payment again, as they will not be able to create another `InvoicePayment` for the same invoice.

If `paymentProvider` rejects the payment, the code will remove related `InvoicePayment` row from DB. This wil allow the payment to be performed again in the future for this `Invoice`.

If `paymentProvider` throws exception, or the code execution ceases for any reason (hardware failure, etc.) the `InvoicePayment` will stay in DB in it's current state (with status `IN_PROGRESS`). That means the payment can be either successful or not, but code was unable to determinate it and hence this invoice must be reviewed manually and handled. Any code that tries to call `BillingService.chargeInvoice` for this invoice will fail as `InvoicePayment` row still exists for it.

### Testability

Since business logic here depends heavily on SQL database behavior and characteristics (like constraints, primary keys, etc.), proper unit testing is a challenge. Since DAL is mocked, unit tests rely on partially mocked business logic and have to be taken with the grain of salt. Also concurrency problems are difficult to address in unit tests.

The solution to that is to not forget about other test methods such as integration testing, system testing, stress testing.
