package com.kafkastream.web.kafkarest;

import com.kafkastream.constants.KafkaConstants;
import com.kafkastream.dto.CustomerDto;
import com.kafkastream.dto.CustomerOrderDTO;
import com.kafkastream.dto.GreetingDto;
import com.kafkastream.dto.OrderDto;
import com.kafkastream.model.Customer;
import com.kafkastream.model.CustomerOrder;
import com.kafkastream.model.Greetings;
import com.kafkastream.model.Order;
import com.kafkastream.util.HostStoreInfo;
import com.kafkastream.util.MetadataService;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.state.*;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


@Path("store")
public class StateStoreRestService
{
    private final KafkaStreams streams;
    private final MetadataService metadataService;
    private final HostInfo hostInfo;
    private Server jettyServer;

    public StateStoreRestService(final KafkaStreams streams, final HostInfo hostInfo)
    {
        this.streams = streams;
        this.metadataService = new MetadataService(streams);
        this.hostInfo = hostInfo;
    }

    private static <T> T waitUntilStoreIsQueryable(final String storeName, final QueryableStoreType<T> queryableStoreType, final KafkaStreams streams) throws InterruptedException
    {
        while (true)
        {
            try
            {
                Collection<StreamsMetadata> streamsMetadataCollection = streams.allMetadata();
                for (StreamsMetadata streamsMetadata : streamsMetadataCollection)
                {
                    System.out.println("streamsMetadataIterator.next() -> " + streamsMetadata);
                }
                return streams.store(storeName, queryableStoreType);
            } catch (InvalidStateStoreException ignored)
            {
                // store not yet ready for querying
                Thread.sleep(1000);
            }
        }
    }

    @GET
    @Path("/customer-order/{customerId}")
    @Produces(MediaType.APPLICATION_JSON)
    public List<CustomerOrderDTO> getCustomerOrder(@PathParam("customerId") String customerId) throws InterruptedException
    {
        System.out.println("Inside getCustomerOrder()");
        List<CustomerOrderDTO> customerOrderList = new ArrayList<>();
        ReadOnlyKeyValueStore<String, CustomerOrder> customerOrdersStore = waitUntilStoreIsQueryable(KafkaConstants.CUSTOMER_ORDER_STORE_NAME, QueryableStoreTypes.keyValueStore(), streams);

        KeyValueIterator<String, CustomerOrder> keyValueIterator = customerOrdersStore.all();
        while (keyValueIterator.hasNext())
        {
            KeyValue<String, CustomerOrder> customerOrderKeyValue = keyValueIterator.next();
            if (customerOrderKeyValue.value.getCustomerId().toString().equals(customerId))
            {
                customerOrderList.add(getCustomerOrderDTOFromCustomerOrder(customerOrderKeyValue.value));
            }

        }
        return customerOrderList;
    }

    @GET
    @Path("/customer-order/all")
    @Produces(MediaType.APPLICATION_JSON)
    public List<CustomerOrderDTO> getAllCustomersOrders() throws InterruptedException
    {
        System.out.println("Inside getAllCustomersOrders()");
        List<CustomerOrderDTO> customerOrderList = new ArrayList<>();
        ReadOnlyKeyValueStore<String, CustomerOrder> customerOrdersStore = waitUntilStoreIsQueryable(KafkaConstants.CUSTOMER_ORDER_STORE_NAME, QueryableStoreTypes.keyValueStore(), streams);

        KeyValueIterator<String, CustomerOrder> keyValueIterator = customerOrdersStore.all();
        while (keyValueIterator.hasNext())
        {
            KeyValue<String, CustomerOrder> customerOrderKeyValue = keyValueIterator.next();
            customerOrderList.add(getCustomerOrderDTOFromCustomerOrder(customerOrderKeyValue.value));
        }
        return customerOrderList;
    }

    @GET
    @Path("/customers")
    @Produces(MediaType.APPLICATION_JSON)
    public List<CustomerDto> getAllCustomers() throws InterruptedException
    {
        System.out.println("Inside getAllCustomers()");
        List<CustomerDto> customersDtoList = new ArrayList<>();
        ReadOnlyKeyValueStore<String, Customer> customersStore = waitUntilStoreIsQueryable(KafkaConstants.CUSTOMER_STORE_NAME, QueryableStoreTypes.keyValueStore(), streams);

        KeyValueIterator<String, Customer> keyValueIterator = customersStore.all();
        while (keyValueIterator.hasNext())
        {
            Customer customer=keyValueIterator.next().value;
            customersDtoList.add(new CustomerDto(customer.getCustomerId().toString(),customer.getFirstName().toString(), customer.getLastName().toString(),customer.getEmail().toString(),customer.getPhone().toString()));
        }
        return customersDtoList;
    }

    @GET
    @Path("/orders")
    @Produces(MediaType.APPLICATION_JSON)
    public List<OrderDto> getAllOrders() throws InterruptedException
    {
        List<OrderDto> orderDtoList = new ArrayList<>();
        ReadOnlyKeyValueStore<String, Order> ordersStore = waitUntilStoreIsQueryable(KafkaConstants.ORDER_STORE_NAME, QueryableStoreTypes.keyValueStore(), streams);

        KeyValueIterator<String, Order> keyValueIterator = ordersStore.all();
        while (keyValueIterator.hasNext())
        {
            Order order=keyValueIterator.next().value;
            orderDtoList.add(new OrderDto(order.getOrderId().toString(),order.getCustomerId().toString(), order.getOrderItemName().toString(),order.getOrderPlace().toString(),order.getOrderPurchaseTime().toString()));
        }
        return orderDtoList;
    }

    @GET
    @Path("/greetings")
    @Produces(MediaType.APPLICATION_JSON)
    public List<GreetingDto> getAllGreetings() throws InterruptedException
    {
        List<GreetingDto> greetingDtoList = new ArrayList<>();
        ReadOnlyKeyValueStore<String, Greetings> ordersStore = waitUntilStoreIsQueryable(KafkaConstants.GREETING_STORE_NAME, QueryableStoreTypes.keyValueStore(), streams);

        KeyValueIterator<String, Greetings> keyValueIterator = ordersStore.all();
        while (keyValueIterator.hasNext())
        {
            Greetings greetings=keyValueIterator.next().value;
            greetingDtoList.add(new GreetingDto(greetings.getMessage().toString(),greetings.getTimestamp().toString()));
        }
        return greetingDtoList;
    }

    @GET()
    @Path("/instances")
    @Produces(MediaType.APPLICATION_JSON)
    public List<HostStoreInfo> streamsMetadata()
    {
        return metadataService.streamsMetadata();
    }

    @GET()
    @Path("/instances/{storeName}")
    @Produces(MediaType.APPLICATION_JSON)
    public List<HostStoreInfo> streamsMetadataForStore(@PathParam("storeName") String store)
    {
        return metadataService.streamsMetadataForStore(store);
    }


    private CustomerOrderDTO getCustomerOrderDTOFromCustomerOrder(CustomerOrder customerOrder)
    {
        return new CustomerOrderDTO(customerOrder.getCustomerId().toString(), customerOrder.getFirstName().toString(), customerOrder.getLastName().toString(), customerOrder.getEmail().toString(), customerOrder.getPhone().toString(), customerOrder.getOrderId().toString(), customerOrder.getOrderItemName().toString(), customerOrder.getOrderPlace().toString(), customerOrder.getOrderPurchaseTime().toString());
    }

    public void start() throws Exception
    {
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");

        jettyServer = new Server(hostInfo.port());
        jettyServer.setHandler(context);

        ResourceConfig rc = new ResourceConfig();
        rc.register(this);
        rc.register(JacksonFeature.class);

        ServletContainer sc = new ServletContainer(rc);
        ServletHolder holder = new ServletHolder(sc);
        context.addServlet(holder, "/*");

        jettyServer.start();
    }

    @GET
    @Path("/customer/{customerId}")
    @Produces(MediaType.APPLICATION_JSON)
    public CustomerDto getCustomerInformation(@PathParam("customerId") String customerId)
    {
        try
        {
            ReadOnlyKeyValueStore<String, Customer> customersStore = waitUntilStoreIsQueryable(KafkaConstants.CUSTOMER_STORE_NAME, QueryableStoreTypes.keyValueStore(), streams);
            KeyValueIterator<String, Customer> keyValueIterator = customersStore.all();
            while (keyValueIterator.hasNext())
            {
                Customer customer=keyValueIterator.next().value;
                if(customer.getCustomerId().toString().equals(customerId))
                    return new CustomerDto(customer.getCustomerId().toString(),customer.getFirstName().toString(),customer.getLastName().toString(),
                            customer.getEmail().toString(),customer.getPhone().toString());
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return null;
    }


    public Customer getCustomer(String customerId)
    {
        try
        {
            ReadOnlyKeyValueStore<String, Customer> customersStore = waitUntilStoreIsQueryable(KafkaConstants.CUSTOMER_STORE_NAME, QueryableStoreTypes.keyValueStore(), streams);
            KeyValueIterator<String, Customer> keyValueIterator = customersStore.all();
            while (keyValueIterator.hasNext())
            {
                if(keyValueIterator.next().value.getCustomerId().equals(customerId))
                    return keyValueIterator.next().value;
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return null;
    }
}
