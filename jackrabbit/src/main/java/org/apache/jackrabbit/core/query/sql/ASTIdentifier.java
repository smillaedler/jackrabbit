/*
 * Copyright 2004-2005 The Apache Software Foundation or its licensors,
 *                     as applicable.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.core.query.sql;

import org.apache.jackrabbit.name.QName;

public class ASTIdentifier extends SimpleNode {

    private QName name;

    public ASTIdentifier(int id) {
    super(id);
  }

  public ASTIdentifier(JCRSQLParser p, int id) {
    super(p, id);
  }

    public void setName(QName name) {
        this.name = name;
    }

    public QName getName() {
        return name;
    }

  /** Accept the visitor. **/
  public Object jjtAccept(JCRSQLParserVisitor visitor, Object data) {
    return visitor.visit(this, data);
  }

    public String toString() {
        return super.toString() + ": " + name;
    }
}
