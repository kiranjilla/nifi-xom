package org.apache.nifi.domain.opcda;

import lombok.Data;
import org.openscada.opc.lib.da.Item;
import org.openscada.opc.lib.da.ItemState;

/**
 * Created by frank on 2016-12-28.
 */

@Data
public class OPCDATag {

    private Item item;

    private ItemState itemState;

    public OPCDATag(Item item, ItemState itemState) {
        this.item = item;
        this.itemState = itemState;
    }

}
