package org.oxbow.swingbits.list;

import java.util.ArrayList;
import java.util.Collection;
import java.util.ResourceBundle;

public interface ICheckListAction<T> {

    ResourceBundle BUNDLE = ResourceBundle.getBundle("task-dialog");

    void check(ICheckListModel<T> model, boolean value);

    public static class CheckAll<T> implements ICheckListAction<T> {

	@Override
	public String toString() {
	    return BUNDLE.getString("Show_All_Columns");
	}

	@SuppressWarnings("unchecked")
	@Override
	public void check(ICheckListModel<T> model, boolean value) {
	    Collection<T> items = new ArrayList<T>();
	    if (value) {
		for (int i = 0, s = model.getSize(); i < s; i++) {
		    items.add((T) model.getElementAt(i));
		}
	    }
	    model.setCheckedItems(items);

	}

    }

}