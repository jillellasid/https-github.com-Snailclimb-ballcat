package com.hccake.ballcat.common.datascope.processor;

import com.hccake.ballcat.common.datascope.DataScope;
import com.hccake.ballcat.common.datascope.holder.DataScopeMatchNumHolder;
import com.hccake.ballcat.common.datascope.parser.JsqlParserSupport;
import com.hccake.ballcat.common.datascope.util.CollectionUtils;
import com.hccake.ballcat.common.datascope.util.SqlParseUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.NotExpression;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.ExistsExpression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.ItemsList;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.LateralSubSelect;
import net.sf.jsqlparser.statement.select.ParenthesisFromItem;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.SubJoin;
import net.sf.jsqlparser.statement.select.SubSelect;
import net.sf.jsqlparser.statement.select.ValuesList;
import net.sf.jsqlparser.statement.select.WithItem;
import net.sf.jsqlparser.statement.update.Update;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * ???????????? sql ????????? ?????? mybatis-plus ???????????????????????? sql where ????????????????????????????????????
 *
 * @author Hccake 2020/9/26
 * @version 1.0
 */
@RequiredArgsConstructor
@Slf4j
public class DataScopeSqlProcessor extends JsqlParserSupport {

	/**
	 * select ??????SQL??????
	 * @param select jsqlparser Statement Select
	 */
	@Override
	protected void processSelect(Select select, int index, String sql, Object obj) {
		List<DataScope> dataScopes = (List<DataScope>) obj;
		try {
			// dataScopes ?????? ThreadLocal ????????????
			DataScopeHolder.set(dataScopes);
			processSelectBody(select.getSelectBody());
			List<WithItem> withItemsList = select.getWithItemsList();
			if (CollectionUtils.isNotEmpty(withItemsList)) {
				withItemsList.forEach(this::processSelectBody);
			}
		}
		finally {
			// ???????????? ThreadLocal
			DataScopeHolder.remove();
		}
	}

	protected void processSelectBody(SelectBody selectBody) {
		if (selectBody == null) {
			return;
		}
		if (selectBody instanceof PlainSelect) {
			processPlainSelect((PlainSelect) selectBody);
		}
		else if (selectBody instanceof WithItem) {
			WithItem withItem = (WithItem) selectBody;
			processSelectBody(withItem.getSubSelect().getSelectBody());
		}
		else {
			SetOperationList operationList = (SetOperationList) selectBody;
			List<SelectBody> selectBodys = operationList.getSelects();
			if (CollectionUtils.isNotEmpty(selectBodys)) {
				selectBodys.forEach(this::processSelectBody);
			}
		}
	}

	/**
	 * insert ??????SQL??????
	 * @param insert jsqlparser Statement Insert
	 */
	@Override
	protected void processInsert(Insert insert, int index, String sql, Object obj) {
		// insert ???????????????
	}

	/**
	 * update ??????SQL??????
	 * @param update jsqlparser Statement Update
	 */
	@Override
	protected void processUpdate(Update update, int index, String sql, Object obj) {
		List<DataScope> dataScopes = (List<DataScope>) obj;
		try {
			// dataScopes ?????? ThreadLocal ????????????
			DataScopeHolder.set(dataScopes);
			update.setWhere(this.injectExpression(update.getWhere(), update.getTable()));
		}
		finally {
			// ???????????? ThreadLocal
			DataScopeHolder.remove();
		}
	}

	/**
	 * delete ??????SQL??????
	 * @param delete jsqlparser Statement Delete
	 */
	@Override
	protected void processDelete(Delete delete, int index, String sql, Object obj) {
		List<DataScope> dataScopes = (List<DataScope>) obj;
		try {
			// dataScopes ?????? ThreadLocal ????????????
			DataScopeHolder.set(dataScopes);
			delete.setWhere(this.injectExpression(delete.getWhere(), delete.getTable()));
		}
		finally {
			// ???????????? ThreadLocal
			DataScopeHolder.remove();
		}
	}

	/**
	 * ?????? PlainSelect
	 */
	protected void processPlainSelect(PlainSelect plainSelect) {
		// #3087 github
		List<SelectItem> selectItems = plainSelect.getSelectItems();
		if (CollectionUtils.isNotEmpty(selectItems)) {
			selectItems.forEach(this::processSelectItem);
		}

		// ?????? where ???????????????
		Expression where = plainSelect.getWhere();
		processWhereSubSelect(where);

		// ?????? fromItem
		FromItem fromItem = plainSelect.getFromItem();
		List<Table> list = processFromItem(fromItem);
		List<Table> mainTables = new ArrayList<>(list);

		// ?????? join
		List<Join> joins = plainSelect.getJoins();
		if (CollectionUtils.isNotEmpty(joins)) {
			mainTables = processJoins(mainTables, joins);
		}

		// ?????? mainTable ???????????? where ????????????
		if (CollectionUtils.isNotEmpty(mainTables)) {
			plainSelect.setWhere(injectExpression(where, mainTables));
		}
	}

	private List<Table> processFromItem(FromItem fromItem) {
		// ?????????????????????????????????
		while (fromItem instanceof ParenthesisFromItem) {
			fromItem = ((ParenthesisFromItem) fromItem).getFromItem();
		}

		List<Table> mainTables = new ArrayList<>();
		// ??? join ??????????????????
		if (fromItem instanceof Table) {
			Table fromTable = (Table) fromItem;
			mainTables.add(fromTable);
		}
		else if (fromItem instanceof SubJoin) {
			// SubJoin ??????????????????????????? where ??????
			List<Table> tables = processSubJoin((SubJoin) fromItem);
			mainTables.addAll(tables);
		}
		else {
			// ????????? fromItem
			processOtherFromItem(fromItem);
		}
		return mainTables;
	}

	/**
	 * ??????where?????????????????????
	 * <p>
	 * ????????????: 1. in 2. = 3. > 4. < 5. >= 6. <= 7. <> 8. EXISTS 9. NOT EXISTS
	 * <p>
	 * ????????????: 1. ????????????????????????????????? 2. ?????????????????????????????????????????????
	 * @param where where ??????
	 */
	protected void processWhereSubSelect(Expression where) {
		if (where == null) {
			return;
		}
		if (where instanceof FromItem) {
			processOtherFromItem((FromItem) where);
			return;
		}
		if (where.toString().indexOf("SELECT") > 0) {
			// ????????????
			if (where instanceof BinaryExpression) {
				// ???????????? , and , or , ??????
				BinaryExpression expression = (BinaryExpression) where;
				processWhereSubSelect(expression.getLeftExpression());
				processWhereSubSelect(expression.getRightExpression());
			}
			else if (where instanceof InExpression) {
				// in
				InExpression expression = (InExpression) where;
				ItemsList itemsList = expression.getRightItemsList();
				if (itemsList instanceof SubSelect) {
					processSelectBody(((SubSelect) itemsList).getSelectBody());
				}
			}
			else if (where instanceof ExistsExpression) {
				// exists
				ExistsExpression expression = (ExistsExpression) where;
				processWhereSubSelect(expression.getRightExpression());
			}
			else if (where instanceof NotExpression) {
				// not exists
				NotExpression expression = (NotExpression) where;
				processWhereSubSelect(expression.getExpression());
			}
			else if (where instanceof Parenthesis) {
				Parenthesis expression = (Parenthesis) where;
				processWhereSubSelect(expression.getExpression());
			}
		}
	}

	protected void processSelectItem(SelectItem selectItem) {
		if (selectItem instanceof SelectExpressionItem) {
			SelectExpressionItem selectExpressionItem = (SelectExpressionItem) selectItem;
			if (selectExpressionItem.getExpression() instanceof SubSelect) {
				processSelectBody(((SubSelect) selectExpressionItem.getExpression()).getSelectBody());
			}
			else if (selectExpressionItem.getExpression() instanceof Function) {
				processFunction((Function) selectExpressionItem.getExpression());
			}
		}
	}

	/**
	 * ????????????
	 * <p>
	 * ??????: 1. select fun(args..) 2. select fun1(fun2(args..),args..)
	 * <p>
	 * <p>
	 * fixed gitee pulls/141
	 * </p>
	 * @param function
	 */
	protected void processFunction(Function function) {
		ExpressionList parameters = function.getParameters();
		if (parameters != null) {
			parameters.getExpressions().forEach(expression -> {
				if (expression instanceof SubSelect) {
					processSelectBody(((SubSelect) expression).getSelectBody());
				}
				else if (expression instanceof Function) {
					processFunction((Function) expression);
				}
			});
		}
	}

	/**
	 * ??????????????????
	 */
	protected void processOtherFromItem(FromItem fromItem) {
		// ????????????
		while (fromItem instanceof ParenthesisFromItem) {
			fromItem = ((ParenthesisFromItem) fromItem).getFromItem();
		}

		if (fromItem instanceof SubSelect) {
			SubSelect subSelect = (SubSelect) fromItem;
			if (subSelect.getSelectBody() != null) {
				processSelectBody(subSelect.getSelectBody());
			}
		}
		else if (fromItem instanceof ValuesList) {
			log.debug("Perform a subquery, if you do not give us feedback");
		}
		else if (fromItem instanceof LateralSubSelect) {
			LateralSubSelect lateralSubSelect = (LateralSubSelect) fromItem;
			if (lateralSubSelect.getSubSelect() != null) {
				SubSelect subSelect = lateralSubSelect.getSubSelect();
				if (subSelect.getSelectBody() != null) {
					processSelectBody(subSelect.getSelectBody());
				}
			}
		}
	}

	/**
	 * ?????? sub join
	 * @param subJoin subJoin
	 * @return Table subJoin ????????????
	 */
	private List<Table> processSubJoin(SubJoin subJoin) {
		List<Table> mainTables = new ArrayList<>();
		if (subJoin.getJoinList() != null) {
			List<Table> list = processFromItem(subJoin.getLeft());
			mainTables.addAll(list);
			mainTables = processJoins(mainTables, subJoin.getJoinList());
		}
		return mainTables;
	}

	/**
	 * ?????? joins
	 * @param mainTables ????????? null
	 * @param joins join ??????
	 * @return List
	 * <Table>
	 * ?????????????????? Table ??????
	 */
	private List<Table> processJoins(List<Table> mainTables, List<Join> joins) {
		if (mainTables == null) {
			mainTables = new ArrayList<>();
		}

		// join ???????????????????????????
		Table mainTable = null;
		// ?????? join ?????????
		Table leftTable = null;
		if (mainTables.size() == 1) {
			mainTable = mainTables.get(0);
			leftTable = mainTable;
		}

		// ?????? on ???????????????????????? join?????????????????????????????? on ?????????
		Deque<List<Table>> onTableDeque = new LinkedList<>();
		for (Join join : joins) {
			// ?????? on ?????????
			FromItem joinItem = join.getRightItem();

			// ???????????? join ?????????subJoint ????????????????????????
			List<Table> joinTables = null;
			if (joinItem instanceof Table) {
				joinTables = new ArrayList<>();
				joinTables.add((Table) joinItem);
			}
			else if (joinItem instanceof SubJoin) {
				joinTables = processSubJoin((SubJoin) joinItem);
			}

			if (joinTables != null) {

				// ????????????????????????
				if (join.isSimple()) {
					mainTables.addAll(joinTables);
					continue;
				}

				// ?????????????????????
				Table joinTable = joinTables.get(0);

				List<Table> onTables = null;
				// ????????????????????????????????????????????????????????????
				if (join.isRight()) {
					mainTable = joinTable;
					if (leftTable != null) {
						onTables = Collections.singletonList(leftTable);
					}
				}
				else if (join.isLeft()) {
					onTables = Collections.singletonList(joinTable);
				}
				else if (join.isInner()) {
					if (mainTable == null) {
						onTables = Collections.singletonList(joinTable);
					}
					else {
						onTables = Arrays.asList(mainTable, joinTable);
					}
					mainTable = null;
				}
				mainTables = new ArrayList<>();
				if (mainTable != null) {
					mainTables.add(mainTable);
				}

				// ?????? join ????????? on ???????????????
				Collection<Expression> originOnExpressions = join.getOnExpressions();
				// ?????? join on ????????????????????????????????????
				if (originOnExpressions.size() == 1 && onTables != null) {
					List<Expression> onExpressions = new LinkedList<>();
					onExpressions.add(injectExpression(originOnExpressions.iterator().next(), onTables));
					join.setOnExpressions(onExpressions);
					leftTable = joinTable;
					continue;
				}
				// ????????????????????????????????? null????????????????????????
				onTableDeque.push(onTables);
				// ???????????? on ??????????????????????????????
				if (originOnExpressions.size() > 1) {
					Collection<Expression> onExpressions = new LinkedList<>();
					for (Expression originOnExpression : originOnExpressions) {
						List<Table> currentTableList = onTableDeque.poll();
						if (CollectionUtils.isEmpty(currentTableList)) {
							onExpressions.add(originOnExpression);
						}
						else {
							onExpressions.add(injectExpression(originOnExpression, currentTableList));
						}
					}
					join.setOnExpressions(onExpressions);
				}
				leftTable = joinTable;
			}
			else {
				processOtherFromItem(joinItem);
				leftTable = null;
			}

		}

		return mainTables;
	}

	/**
	 * ?????? DataScope ????????????????????????????????????????????? where/or ??????
	 * @param currentExpression Expression where/or
	 * @param table ?????????
	 * @return ???????????? where/or ??????
	 */
	private Expression injectExpression(Expression currentExpression, Table table) {
		return injectExpression(currentExpression, Collections.singletonList(table));
	}

	/**
	 * ?????? DataScope ????????????????????????????????????????????? where/or ??????
	 * @param currentExpression Expression where/or
	 * @param tables ?????????
	 * @return ???????????? where/or ??????
	 */
	private Expression injectExpression(Expression currentExpression, List<Table> tables) {
		// ?????????????????????????????????
		if (CollectionUtils.isEmpty(tables)) {
			return currentExpression;
		}

		List<Expression> dataFilterExpressions = new ArrayList<>(tables.size());
		for (Table table : tables) {
			// ????????????
			String tableName = SqlParseUtils.getTableName(table.getName());

			// ?????? dataScope ???????????????
			List<DataScope> matchDataScopes = DataScopeHolder.get().stream()
					.filter(x -> x.getTableNames().contains(tableName)).collect(Collectors.toList());

			if (CollectionUtils.isEmpty(matchDataScopes)) {
				continue;
			}

			// ???????????????
			DataScopeMatchNumHolder.incrementMatchNumIfPresent();

			// ???????????????????????????????????????
			matchDataScopes.stream().map(x -> x.getExpression(tableName, table.getAlias())).filter(Objects::nonNull)
					.reduce(AndExpression::new).ifPresent(dataFilterExpressions::add);
		}

		if (dataFilterExpressions.isEmpty()) {
			return currentExpression;
		}

		// ??????????????????
		Expression injectExpression = dataFilterExpressions.get(0);
		// ???????????????????????? and ??????
		if (dataFilterExpressions.size() > 1) {
			for (int i = 1; i < dataFilterExpressions.size(); i++) {
				injectExpression = new AndExpression(injectExpression, dataFilterExpressions.get(i));
			}
		}

		if (currentExpression == null) {
			return injectExpression;
		}
		if (injectExpression == null) {
			return currentExpression;
		}
		if (currentExpression instanceof OrExpression) {
			return new AndExpression(new Parenthesis(currentExpression), injectExpression);
		}
		else {
			return new AndExpression(currentExpression, injectExpression);
		}
	}

	/**
	 * DataScope ???????????? ???????????? SQL ??????????????????
	 *
	 * @author hccake
	 */
	private static final class DataScopeHolder {

		private DataScopeHolder() {
		}

		private static final ThreadLocal<List<DataScope>> DATA_SCOPES = new ThreadLocal<>();

		/**
		 * get dataScope
		 * @return dataScopes
		 */
		public static List<DataScope> get() {
			return DATA_SCOPES.get();
		}

		/**
		 * ?????? dataScope
		 */
		public static void set(List<DataScope> dataScopes) {
			DATA_SCOPES.set(dataScopes);
		}

		/**
		 * ?????? dataScope
		 */
		public static void remove() {
			DATA_SCOPES.remove();
		}

	}

}
