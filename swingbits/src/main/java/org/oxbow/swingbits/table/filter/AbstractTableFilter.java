/*
 * Copyright (c) 2009-2011, EzWare
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.Redistributions
 * in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.Neither the name of the
 * EzWare nor the names of its contributors may be used to endorse or
 * promote products derived from this software without specific prior
 * written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 */

package org.oxbow.swingbits.table.filter;

import static org.oxbow.swingbits.util.CollectionUtils.*;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.JTable;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;

/**
 * Partial implementation of table filter
 *
 * Created on Feb 10, 2011
 * 
 * @author Eugene Ryzhikov
 *
 * @param <T>
 */
@SuppressWarnings("serial")
public abstract class AbstractTableFilter<T extends JTable> implements ITableFilter<T> {

    private final Set<IFilterChangeListener> listeners = Collections
	    .synchronizedSet(new HashSet<IFilterChangeListener>());

    private final Map<Integer, Collection<DistinctColumnItem>> distinctItemCache = Collections
	    .synchronizedMap(new HashMap<Integer, Collection<DistinctColumnItem>>());

    private final T table;
    private final TableFilterState filterState = new TableFilterState();

    public AbstractTableFilter(T table) {
	this.table = table;
	setupDistinctItemCacheRefresh();
    }

    private void setupDistinctItemCacheRefresh() {
	clearDistinctItemCache();
	listenForDataChange(table.getModel());
	listenForModelChange();
    }

    private void listenForModelChange() {
	table.addPropertyChangeListener("model", new PropertyChangeListener() {
	    @Override
	    public void propertyChange(PropertyChangeEvent e) {
		clearDistinctItemCache();
		listenForDataChange((TableModel) e.getNewValue());
	    }
	});
    }

    private void listenForDataChange(TableModel model) {
	if (model != null) {
	    model.addTableModelListener(new TableModelListener() {
		@Override
		public void tableChanged(TableModelEvent e) {
		    clearDistinctItemCache();

		    for (Integer fCol : new ArrayList<Integer>(filterState.getFilteredColumns())) {
			removeNonExistingFilterValues(fCol, filterState.getValues(fCol));
		    }
		}
	    });
	}
    }

    private void removeNonExistingFilterValues(Integer fCol, Collection<DistinctColumnItem> filterForCol) {
	if (!filterForCol.isEmpty()) {
	    Collection<DistinctColumnItem> columnItems = getDistinctColumnItems(fCol);

	    Iterator<DistinctColumnItem> itFilterForCol = filterForCol.iterator();
	    while (itFilterForCol.hasNext()) {
		DistinctColumnItem colValueChecked = itFilterForCol.next();

		if (!columnItems.contains(colValueChecked)) {
		    itFilterForCol.remove();
		}
	    }
	}
    }

    private void clearDistinctItemCache() {
	distinctItemCache.clear();

	Collection<Integer> filteredColumns = filterState.getFilteredColumns();
	int posFilteredCol = 0;
	boolean filteredColExisting = false;

	for (Integer filteredCol : filteredColumns) {
	    boolean lastFilteredCol = posFilteredCol == filteredColumns.size() - 1;

	    getDistinctColumnItems(filteredCol, filteredColExisting || !lastFilteredCol);

	    if (isFiltered(filteredCol)) {
		filteredColExisting = true;
	    }

	    posFilteredCol++;
	}
    }

    private void clearDistinctItemCacheAfterApply(int appliedColumn) {
	for (Integer col : new ArrayList<Integer>(distinctItemCache.keySet())) {
	    if (col != appliedColumn) {
		distinctItemCache.remove(col);

		if (isFiltered(col)) {
		    collectDistinctColumnItems(col, true);
		}
	    }
	}
    }

    @Override
    public T getTable() {
	return table;
    }

    protected abstract boolean execute(int col, Collection<DistinctColumnItem> items);

    @Override
    public boolean apply(int col, Collection<DistinctColumnItem> items) {
	removeNonExistingFilterValues(col, items);

	setFilterState(col, items);

	boolean result = false;
	if (result = execute(col, items)) {
	    clearDistinctItemCacheAfterApply(col);

	    fireFilterChange();
	}

	return result;
    }

    @Override
    public final void addChangeListener(IFilterChangeListener listener) {
	if (listener != null) {
	    listeners.add(listener);
	}
    }

    @Override
    public final void removeChangeListener(IFilterChangeListener listener) {
	if (listener != null) {
	    listeners.remove(listener);
	}
    }

    public final void fireFilterChange() {
	for (IFilterChangeListener l : listeners) {
	    l.filterChanged(AbstractTableFilter.this);
	}
    }

    private Collection<DistinctColumnItem> getDistinctColumnItems(int column, boolean collectOnlyIfRowShowing) {
	Collection<DistinctColumnItem> result = distinctItemCache.get(column);
	if (result == null) {
	    result = collectDistinctColumnItems(column, collectOnlyIfRowShowing);
	    distinctItemCache.put(column, result);
	}
	return result;

    }

    @Override
    public Collection<DistinctColumnItem> getDistinctColumnItems(int column) {
	return getDistinctColumnItems(column, true);
    }

    private Collection<DistinctColumnItem> collectDistinctColumnItems(int column, boolean collectOnlyIfRowShowing) {
	Set<DistinctColumnItem> result = new TreeSet<DistinctColumnItem>(); // to collect unique items
	for (int row = 0; row < table.getModel().getRowCount(); row++) {
	    Object value = table.getModel().getValueAt(row, column);

	    if (!collectOnlyIfRowShowing || isRowShowing(row, column)) {
		result.add(new DistinctColumnItem(value));
	    }
	}

	return result;
    }

    private boolean isRowShowing(int row, int sourceColumn) {
	for (int col = 0; col < table.getModel().getColumnCount(); col++) {
	    if (col != sourceColumn) {
		Object val = table.getModel().getValueAt(row, col);

		if (!isEmpty(filterState.getValues(col))
			&& !filterState.getValues(col).contains(new DistinctColumnItem(val))) {
		    return false;
		}
	    }
	}

	return true;
    }

    @Override
    public Collection<DistinctColumnItem> getFilterState(int column) {
	return filterState.getValues(column);
    }

    @Override
    public boolean isFiltered(int column) {
	Collection<DistinctColumnItem> checks = getFilterState(column);
	return !isEmpty(checks) && getDistinctColumnItems(column).size() != checks.size();
    }

    @Override
    public boolean includeRow(ITableFilter.Row row) {
	return filterState.include(row);
    }

    public void setFilterState(int column, Collection<DistinctColumnItem> values) {
	filterState.setValues(column, values);
    }

    public void clear() {
	filterState.clear();

	Collection<DistinctColumnItem> items = Collections.emptyList();
	for (int column = 0; column < table.getModel().getColumnCount(); column++) {
	    execute(column, items);
	}

	clearDistinctItemCacheAfterApply(-1);

	fireFilterChange();
    }

}
