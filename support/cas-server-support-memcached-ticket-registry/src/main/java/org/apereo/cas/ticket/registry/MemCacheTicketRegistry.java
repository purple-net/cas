package org.apereo.cas.ticket.registry;

import net.spy.memcached.MemcachedClientIF;
import org.apereo.cas.ticket.Ticket;
import org.springframework.util.Assert;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Key-value ticket registry implementation that stores tickets in memcached keyed on the ticket ID.
 *
 * @author Scott Battaglia
 * @author Marvin S. Addison
 * @since 3.3
 */
public class MemCacheTicketRegistry extends AbstractTicketRegistry {

    /**
     * Memcached client.
     */
    private final MemcachedClientIF client;

    /**
     * Creates a new instance using the given memcached client instance, which is presumably configured via
     * {@code net.spy.memcached.spring.MemcachedClientFactoryBean}.
     *
     * @param client Memcached client.
     */
    public MemCacheTicketRegistry(final MemcachedClientIF client) {
        this.client = client;
    }

    @Override
    public Ticket updateTicket(final Ticket ticketToUpdate) {
        Assert.notNull(this.client, "No memcached client is defined.");

        final Ticket ticket = encodeTicket(ticketToUpdate);
        logger.debug("Updating ticket [{}]", ticket);
        try {
            if (!this.client.replace(ticket.getId(), getTimeout(ticketToUpdate), ticket).get()) {
                logger.error("Failed to update [{}]", ticket);
                return null;
            }
        } catch (final InterruptedException e) {
            logger.warn("Interrupted while waiting for response to async replace operation for ticket [{}]. "
                    + "Cannot determine whether update was successful.", ticket);
        } catch (final Exception e) {
            logger.error("Failed updating [{}]", ticket, e);
        }
        return ticket;
    }

    @Override
    public void addTicket(final Ticket ticketToAdd) {
        Assert.notNull(this.client, "No memcached client is defined.");
        try {
            final Ticket ticket = encodeTicket(ticketToAdd);
            logger.debug("Adding ticket [{}]", ticket);
            final int timeout = getTimeout(ticketToAdd);
            if (!this.client.add(ticket.getId(), getTimeout(ticketToAdd), ticket).get()) {
                logger.error("Failed to add [{}] without timeout [{}]", ticketToAdd, timeout);
            }
            // Sanity check to ensure ticket can retrieved
            if (this.client.get(ticket.getId()) == null) {
                logger.warn("Ticket [{}] was added to memcached with timeout [{}], yet it cannot be retrieved. "
                        + "Ticket expiration policy may be too aggressive ?", ticketToAdd, timeout);
            }
        } catch (final InterruptedException e) {
            logger.warn("Interrupted while waiting for response to async add operation for ticket [{}]."
                    + "Cannot determine whether add was successful.", ticketToAdd);
        } catch (final Exception e) {
            logger.error("Failed adding [{}]", ticketToAdd, e);
        }
    }

    @Override
    public long deleteAll() {
        logger.debug("deleteAll() isn't supported. Returning empty list");
        return 0;
    }

    @Override
    public boolean deleteSingleTicket(final String ticketId) {
        Assert.notNull(this.client, "No memcached client is defined.");
        try {
            if (this.client.delete(ticketId).get()) {
                logger.debug("Removed ticket [{}] from the cache", ticketId);
            } else {
                logger.info("Ticket [{}] not found or is already removed.", ticketId);
            }
        } catch (final Exception e) {
            logger.error("Ticket not found or is already removed. Failed deleting [{}]", ticketId, e);
        }
        return true;
    }

    @Override
    public Ticket getTicket(final String ticketIdToGet) {
        Assert.notNull(this.client, "No memcached client is defined.");

        final String ticketId = encodeTicketId(ticketIdToGet);
        try {
            final Ticket t = (Ticket) this.client.get(ticketId);
            if (t != null) {
                return decodeTicket(t);
            }
        } catch (final Exception e) {
            logger.error("Failed fetching [{}] ", ticketId, e);
        }
        return null;
    }

    @Override
    public Collection<Ticket> getTickets() {
        logger.debug("getTickets() isn't supported. Returning empty list");
        return new ArrayList<>();
    }

    /**
     * Destroy the client and shut down.
     */
    @PreDestroy
    public void destroy() {
        if (this.client == null) {
            return;
        }
        this.client.shutdown();
    }

    /**
     * If not time out value is specified, expire the ticket immediately.
     *
     * @param ticket the ticket
     * @return timeout in milliseconds.
     */
    private static int getTimeout(final Ticket ticket) {
        final int ttl = ticket.getExpirationPolicy().getTimeToLive().intValue();
        if (ttl == 0) {
            return 1;
        }
        return ttl;
    }
}
